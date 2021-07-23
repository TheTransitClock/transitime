package org.transitclock.db.hibernate;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.SQLGrammarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.configData.DbSetupConfig;
import org.transitclock.logging.Markers;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.Time;
import org.transitclock.utils.threading.NamedThreadFactory;

/**
 * Encapsulate the queuing operations of the database.  Make generic so
 * db-side batching is more effective.
 */
public class DbQueue<T> {

  private static final Logger logger = 
      LoggerFactory.getLogger(DbQueue.class);
  
  // For when cannot connect to data the length of time in msec between retries
  private static final long TIME_BETWEEN_RETRIES = 1 * 1000; //msec
  
  private static final int QUEUE_CAPACITY = 500000;

  // The queue that objects to be stored are placed in
  private BlockingQueue<T> queue = new LinkedBlockingQueue<T>(QUEUE_CAPACITY);
  

  // When running in playback mode where getting AVLReports from database
  // instead of from an AVL feed, then debugging and don't want to store
  // derived data into the database because that would interfere with the
  // derived data that was already stored in real time. For that situation
  // shouldStoreToDb should be set to false.
  private final boolean shouldStoreToDb;

  // Used by add(). If queue filling up to 25% and shouldPauseToReduceQueue is
  // true then will pause the calling thread for a few seconds so that more
  // objects can be written out and not have the queue fill up.
  private final boolean shouldPauseToReduceQueue;

  // The queue capacity levels when an error message should be e-mailed out. 
  // The max value should be 1.0. 
  private final double levels[] = { 0.5, 0.8, 1.00 };
  
  // For keeping track of index into levels, which level of capacity of
  // queue being used. When level changes then an e-mail is sent out warning
  // the operators.
  private double indexOfLevelWhenMessageLogged = 0;
  
  // For keeping track of maximum capacity of queue that was used. 
  // Used for logging when queue use is going down.
  private double maxQueueLevel = 0.0;
  
  
  // So can access projectId for logging messages
  private String projectId;

  // The Session for writing data to db
  private SessionFactory sessionFactory;
  
  // collect some statistics on how the db is performing
  private long throughputCount = 0;
  private long throughputTimestamp = System.currentTimeMillis();
  private String shortType;

  public DbQueue(String projectId, boolean shouldStoreToDb, 
      boolean shouldPauseToReduceQueue, String shortType) {
    this.projectId = projectId;
    this.shouldStoreToDb = shouldStoreToDb;
    this.shouldPauseToReduceQueue = shouldPauseToReduceQueue;
    this.shortType = shortType;
    
    
    // Create the reusable heavy weight session factory
    sessionFactory = HibernateUtils.getSessionFactory(projectId);
    
    // Start up separate thread that reads from the queue and
    // actually stores the data
    NamedThreadFactory threadFactory = new NamedThreadFactory(getClass().getSimpleName());
    ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    executor.execute(new Runnable() {
      public void run() {
        processData();
        }
      });
    ThroughputMonitor tm = new ThroughputMonitor();
    new Thread(tm).start();

  }
  
  public boolean add(T t) {
    // If in playback mode then don't want to store the
    // derived data because it would interfere with the
    // derived data already stored when was running in real time.
    if (!shouldStoreToDb)
      return true;
    
    // Add the object to the queue
    boolean success = queue.offer(t);

    double level = queueLevel();
    int levelIndex = indexOfLevel(level);
    // If reached a new level then output message e-mail to warn users
    if (levelIndex > indexOfLevelWhenMessageLogged) {
      indexOfLevelWhenMessageLogged = levelIndex;
      String message = success ?
          "DataDbLogger queue filling up " +
          " for projectId=" + projectId +" and type " + shortType + ". It is now at " + 
          String.format("%.1f", level*100) + "% capacity with " + 
          queue.size() + " elements already in the queue."
          :
          "DataDbLogger queue is now completely full for projectId=" + 
          projectId + ". LOSING DATA!!!"; 
      logger.error(Markers.email(), message);
    }
    
    // If losing data then log such
    if (!success) {
      logger.error("DataDbLogger queue is now completely full for " +
          "projectId=" + projectId + "and type " + shortType + ". LOSING DATA!!! Failed to " +
          "store object=[" + t + "]");
    }
    
    // Keep track of max queue level so can log it when queue level 
    // is decreasing again.
    if (level > maxQueueLevel)
      maxQueueLevel = level;
    
    // If shouldPauseToReduceQueue (because in batch mode or such) and
    // if queue is starting to get more full then pause the calling
    // thread for 10 seconds so that separate thread can clear out 
    // queue a bit.
    if (shouldPauseToReduceQueue && level > 0.2) {
      logger.info("Pausing thread adding data to DataDbLogger queue " +
          "so that queue can be cleared out. Level={}%, type=", 
          level*100.0, shortType);
      Time.sleep(10 * Time.MS_PER_SEC);
    }
    
    // Return whether was successful in adding object to queue
    return success;

  }

  private List<T> drain() {
    // Get the next object from the head of the queue
    ArrayList<T> buff = new ArrayList<T>(DbSetupConfig.getBatchSize());
    int count = 0;
    do {
        buff.clear();
        count = queue.drainTo(buff, DbSetupConfig.getBatchSize());
        throughputCount += count;
        if (count == 0)
          try {
            Thread.sleep(TIME_BETWEEN_RETRIES);
          } catch (InterruptedException e) {
          }
    } while (buff.isEmpty());
    logger.debug("drained {} elements", count);
    // Log if went below a capacity level
    // See if queue dropped to 10% less than the previously logged level.
    // Use a margin of 10% so that don't get flood of messages if queue
    // oscillating around a level.
    double level = queueLevel();
    int levelIndexIncludingMargin = indexOfLevel(level + 0.10);
    if (levelIndexIncludingMargin < indexOfLevelWhenMessageLogged) {
      logger.error(Markers.email(), "DataDbLogger queue emptying out somewhat " +
          " for projectId=" + projectId + " and type " + shortType + ". It is now at " + 
          String.format("%.1f", level*100) + "% capacity with " + queue.size() + 
          " elements already in the queue. The maximum capacity was " +
          String.format("%.1f", maxQueueLevel*100) + "%.");
      indexOfLevelWhenMessageLogged = levelIndexIncludingMargin;
      
      // Reset the maxQueueLevel so can determine what next peak is
      maxQueueLevel = level;
    }

    // Return the result
    return buff;
  }

  /**
   * Process a batch of data, as specified by BATCH_SIZE member. The goal
   * is to batch a few db writes together to reduce load on network and on
   * db machines. There this method will try to store multiple objects
   * from the queue at once, up to the BATCH_SIZE.
   * 
   * If there is an exception with an object being written then the
   * batch of objects will be written individually so that all of the
   * good data will still be stored.
   * 
   *  When looked at Hibernate documentation on batch writing there is
   *  mention of using:
   *      if (++batchingCounter % BATCH_SIZE == 0) {
   *        session.flush();
   *        session.clear();
   *      }
   * But the above doesn't commit the data to the db until the transaction
   * commit is done. Therefore the need here isn't true Hibernate batch
   * processing. Instead, need to use a transaction for each batch.
   */
  	public void processBatchOfData() {
		// Create an array for holding what is being written to db. If there
		// is an exception with one of the objects, such as a constraint violation,
		// then can try to write the objects one at a time to make sure that the
		// the good ones are written. This way don't lose any good data even if
		// an exception occurs while batching data.
		List<Object> objectsForThisBatch = new ArrayList<Object>(DbSetupConfig.getBatchSize());
		
		Transaction tx = null;
		Session session = null;
		
		try {			
			session = sessionFactory.openSession();
			tx = session.beginTransaction();			

      // Get the objects to be stored from the queue
      List<T> objectsToBeStored = drain();
      
      objectsForThisBatch.addAll(objectsToBeStored);
			for (Object objectToBeStored : objectsForThisBatch) {				
				// Write the data to the session. This doesn't yet
				// actually write the data to the db though. That is only
				// done when the session is flushed or committed.
				logger.debug("DataDbLogger batch saving object={}", 
						objectToBeStored);
				session.save(objectToBeStored);
			}
			
			// Sometimes useful for debugging via the console
			//System.err.println(new Date() + " Committing " 
			//		+ objectsForThisBatch.size() + " objects. " + queueSize() 
			//		+ " objects still in queue.");			
			logger.debug("Committing {} objects. {} objects still in queue.", 
					objectsForThisBatch.size(), queueSize());			
			IntervalTimer timer = new IntervalTimer();

			// Actually do the commit
			tx.commit();
			
			// Sometimes useful for debugging via the console
			//System.err.println(new Date() + " Done committing. Took " 
			//		+ timer.elapsedMsec() + " msec");
			logger.debug("Done committing. Took {} msec", timer.elapsedMsec());
			
			session.close();
		} catch (HibernateException e) {
			e.printStackTrace();
			
			// If there was a connection problem then create a whole session
			// factory so that get new connections.
			Throwable rootCause = HibernateUtils.getRootCause(e);
			
			if (rootCause instanceof SocketTimeoutException || rootCause instanceof SocketException
					|| (rootCause instanceof SQLException 
							&& rootCause.getMessage().contains("statement closed"))) {
				logger.error(Markers.email(),
						"Had a connection problem to the database. Likely "
						+ "means that the db was rebooted or that the "
						+ "connection to it was lost. Therefore creating a new "
						+ "SessionFactory so get new connections.");
				HibernateUtils.clearSessionFactory();
				sessionFactory = HibernateUtils.getSessionFactory(projectId);
			} else {
				// Rollback the transaction since it likely was not committed.
				// Otherwise can get an error when using Postgres "ERROR:
				// current transaction is aborted, commands ignored until end of
				// transaction block".
				try {
					if (tx != null)
						tx.rollback();
				} catch (HibernateException e2) {
					logger.error(
							"Error rolling back transaction after processing "
									+ "batch of data via DataDbLogger.", e2);
				}

				// Close session here so that can process the objects
				// individually
				// using a new session.
				try {
					if (session != null)
						session.close();
				} catch (HibernateException e2) {
					logger.error("Error closing session after processing "
							+ "batch of data via DataDbLogger.", e2);
				}
			
				// If it is a SQLGrammarException then also log the SQL to
				// help in debugging.
				String additionaInfo = e instanceof SQLGrammarException ? 
						" SQL=\"" + ((SQLGrammarException) e).getSQL() + "\""
						: "";
				Throwable cause = HibernateUtils.getRootCause(e);
				logger.error("{} for database for project={} when batch writing "
						+ "objects: {}. Will try to write each object "
						+ "from batch individually. {}", 
						e.getClass().getSimpleName(), projectId,
						cause.getMessage(), additionaInfo);
			}
			
			// Write each object individually so that the valid ones will be
			// successfully written.
			for (Object o : objectsForThisBatch) {
				boolean shouldKeepTrying = false;
				do {
					try {
						processSingleObject(o);
						shouldKeepTrying = false;
					} catch (HibernateException e2) {
						if(e instanceof ConstraintViolationException && e2 instanceof GenericJDBCException) {
							shouldKeepTrying = false;
						} else {
							// Need to know if it is a problem with the database not
							// being accessible or if there is a problem with the SQL/data.
							// If there is a problem accessibility of the database then
							// want to keep trying writing the old data. But if it is
							// a problem with the SQL/data then only want to try to write
							// the good data from the batch a single time to make sure 
							// all good data is written.
							if (shouldKeepTryingBecauseConnectionException(e2)) {
								shouldKeepTrying = true;
								logger.error("Encountered database connection " +
										"exception so will sleep for {} msec and " +
										"will then try again.", TIME_BETWEEN_RETRIES);
								Time.sleep(TIME_BETWEEN_RETRIES);
							}
						}
						// Output message on what is going on
						Throwable cause2 = HibernateUtils.getRootCause(e2);
						logger.error(e2.getClass().getSimpleName() + " when individually writing object " +
								o + ". " + 
								(shouldKeepTrying?"Will keep trying. " : "") +
								"msg=" + cause2.getMessage()); 
					}
				} while (shouldKeepTrying);
			}
		}
	}
	
  
  /**
   * This is the main method for processing data. It simply keeps on calling
   * processBatchOfData() so that data is batched as efficiently as possible.
   * Exceptions are caught such that this method will continue to run
   * indefinitely.
   */
  public void processData() {
    while (true) {
      try {
        logger.debug("DataDbLogger.processData() processing batch of " +
            "data to be stored in database.");
        processBatchOfData();
      } catch (Exception e) {
        logger.error("Error writing data to database via DataDbLogger. " +
            "Look for ERROR in log file to see if the database classes " +
            "were configured correctly. Error: "
            + e);
        
        // Don't try again right away because that would be wasteful
        Time.sleep(TIME_BETWEEN_RETRIES);
      }
    }
  }

  
  
  
  /**
   * Returns how much capacity of the queue is being used up. 
   * 
   * @return a value between 0.0 and 1.0 indicating how much of queue being used
   */
  public double queueLevel() {
    int remainingCapacity = queue.remainingCapacity();
    int totalCapacity = queue.size() + remainingCapacity;
    double level = 1.0  - (double) remainingCapacity / totalCapacity;
    return level;
  }
  
  /**
   * Returns how many items are in queue to be processed
   * @return items in queue
   */
  public int queueSize() {
    return queue.size();
  }
  
  /**
   * Returns the index into levels that the queue capacity is at.
   * For determining if should send e-mail warning message.
   * 
   * @param queueLevel
   * @return
   */
  private int indexOfLevel(double queueLevel) {
    for (int i=0; i<levels.length; ++i) {
      if (queueLevel < levels[i])
        return i;
    }
    // Must be level of 1.0 so return full size of levels array
    return levels.length;
  }

  /**
   * Determines highest level cause of exception. Useful
   * for determine the root cause of the exception so that
   * appropriate error message can be displayed.
   * @param e
   * @return
   */
  @SuppressWarnings("unused")
  private Throwable getRootCause(Exception e) {
    Throwable prev =  e;
    while (true) {
      Throwable next = prev.getCause();
      if (next == null) 
        return prev;
      else
        prev = next;
    }
  }

  
  /**
   * Returns true if the exception indicates that there is a problem connecting
   * to the database as opposed to with the SQL.
   * 
   * @param e
   * @return
   */
  private boolean shouldKeepTryingBecauseConnectionException(HibernateException e) {
    // Need to know if it is a problem with the database not
    // being accessible or if there is a problem with the SQL/data.
    // If there is a problem accessibility of the database then
    // want to keep trying writing the old data. But if it is
    // a problem with the SQL/data then only want to try to write
    // the good data from the batch a single time to make sure 
    // all good data is written.
    // From javadocs for for org.hivernate.exception at
    // http://docs.jboss.org/hibernate/orm/3.5/javadocs/org/hibernate/exception/package-frame.html 
    // can see that there are a couple of different exception types. 
    // From looking at documentation and testing found out that 
    // bad SQL is indicated by 
    //   ConstraintViolationException
    //   DataException
    //   SQLGrammarException
    // Appears that for bad connection could get:
    //   JDBCConnectionException (was not able to verify experimentally)
    //   GenericJDBCException    (obtained when committing transaction with db turned off)
    // So if exception is JDBCConnectionException or JDBCGenericException
    // then should keep retrying until successful.
    boolean keepTryingTillSuccessfull = e instanceof JDBCConnectionException ||
                                    e instanceof GenericJDBCException;
    return keepTryingTillSuccessfull;
  }

  /**
   * Store just a single object into data. This is slower than batching a few
   * at a time. Should be used when the batching encounters an exception. This
   * way can still store all of the good data from a batch.
   * 
   * @param o
   */
  private void processSingleObject(Object objectToBeStored) {
    Session session = null;
    try {
      session = sessionFactory.openSession();
      Transaction tx = session.beginTransaction();
      logger.debug("Individually saving object {}", objectToBeStored);
      session.save(objectToBeStored);
      tx.commit();
    } finally {
      if (session != null)
        session.close();
    }
  }

  private class ThroughputMonitor implements Runnable {
    private long interval = 1l;  // minutes
    @Override
    public void run() {
      Time.sleep(interval * Time.MS_PER_MIN);
      while (!Thread.interrupted()) {
        try {
          processThroughput();
        } catch (Throwable t) {
          logger.error("monitor broke:{}", t, t);
        }
        Time.sleep(interval * Time.MS_PER_MIN);
      }
    }
    
    private void processThroughput() {
      long delta = (System.currentTimeMillis() - throughputTimestamp)/1000;
      if (throughputCount == 0) {
        logger.debug("wrote nothing");
        return;
      }
      
      long throughput = throughputCount;
      throughputCount = 0;
      throughputTimestamp = System.currentTimeMillis();
      double rate = throughput / delta;
      logger.info("wrote {} {} messages in {}s, ({}/s) ", throughput, shortType, (long)delta/1000, (long)rate);
    }
  }
}
