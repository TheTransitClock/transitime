/* 
 * This file is part of Transitime.org
 * 
 * Transitime.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * Transitime.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Transitime.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitclock.applications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaExport.Action;
import org.hibernate.tool.schema.TargetType;

import com.google.common.reflect.ClassPath;

/**
 * For generating SQL schema files based on classes to be stored in database
 * that were annotated for Hibernate. This is much nicer than trying to figure
 * out what the schema really should be by hand. This code was copied from
 * http:/
 * /jandrewthompson.blogspot.nl/2009/10/how-to-generate-ddl-scripts-from.html
 * <p>
 * Note that unfortunately there does not appear to be a way to specify the
 * order of the columns in the resulting create table SQL statements. Though
 * this has been asked for, it appears to still be a limitation of Hibernate.
 * The default ordering appears to be first the @Id columns in reverse
 * alphabetical order, and then the non @Id columns in alphabetical order. Yes,
 * quite peculiar.
 * <p>
 * Since the resulting automatically generated files have unneeded drop commands
 * these are filtered out. This way the resulting sql is smaller and easier to
 * understand.
 * 
 * @author john.thompson, Skibu Smith, and Sean Crudden
 *
 */
public class SchemaGenerator {
	
	private final String packageName;
	private final String outputDirectory;
	private static List<Class<Object>> classList=null;
	
	private static final Logger logger =
			LoggerFactory.getLogger(SchemaGenerator.class);
	
	/**
	 * MySQL handles fractional seconds differently from PostGRES and other
	 * DBs. Need to use "datetime(3)" for fractional seconds whereas with 
	 * PostGRES can use the default "timestamp" type. In order to handle
	 * this properly in the generated ddl schema files need to not use
	 * @Column(columnDefinition="datetime(3)") in the Java class that defines
	 * the db object. Instead need to use this special ImprovedMySQLDialect
	 * as the Dialect.
	 */
	public static class ImprovedMySQLDialect extends MySQLDialect {
		public ImprovedMySQLDialect() {
			super();
			// Specify special SQL type for MySQL for timestamps so that get
			// fractions seconds.
			registerColumnType(Types.TIMESTAMP, "datetime(3)");
		}
	}


	@SuppressWarnings("unchecked")
	public SchemaGenerator(String packageName, String outputDirectory) throws Exception {
		
		classList=new ArrayList<Class<Object>>();
		for (Class<Object> clazz : getClasses(packageName)) {
			classList.add(clazz);
		}
		
		this.packageName = packageName;
		this.outputDirectory = outputDirectory;
	}

	/**
	 * Gets rid of the unwanted drop table commands. These aren't needed because
	 * the resulting script is intended only for creating a database, not for
	 * deleting all the data and recreating the tables.
	 * 
	 * @param outputFilename
	 */
	private void trimCruftFromFile(String outputFilename) {
		// Need to write to a temp file because if try to read and write
		// to same file things get quite confused.
		String tmpFileName = outputFilename + "_tmp";
		
		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			FileInputStream fis = new FileInputStream(outputFilename);
			reader = new BufferedReader(new InputStreamReader(fis));

			FileOutputStream fos = new FileOutputStream(tmpFileName);
			writer = new BufferedWriter(new OutputStreamWriter(fos));
			
			String line;
			while ((line = reader.readLine()) != null) {
				// Filter out "drop table" commands
				if (line.contains("drop table")) {
					// Read in following blank line
					line = reader.readLine();
					
					// Continue to next line since filtering out drop table commands
					continue;
				}
				
				// Filter out "drop sequence" oracle commands
				if (line.contains("drop sequence")) {
					// Read in following blank line
					line = reader.readLine();
					
					// Continue to next line since filtering out drop commands
					continue;					
				}
				
				// Filter out the alter table commands where dropping a key or
				// a constraint
				if (line.contains("alter table")) {
					
					if (line.contains("drop")) {
						 
						
						// Continue to next line since filtering out drop commands
						continue;					
					} 
				}
				
				// Line not being filtered so write it to the file
				writer.write(line);
				writer.write("\n");
			}
		} catch (IOException e) {
			System.err.println("Could not trim cruft from file "
					+ outputFilename + " . " + e.getMessage());
		} finally {
			try {
				if (reader != null)
					reader.close();
				if (writer != null)
					writer.close();
			} catch (IOException e) {
			}
		}

		// Move the temp file to the original name
		try {
			Files.copy(new File(tmpFileName).toPath(),
					new File(outputFilename).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			Files.delete(new File(tmpFileName).toPath());
		} catch (IOException e) {
			System.err.println("Could not rename file " + tmpFileName + " to "
					+ outputFilename);
		}

	}
	
	/**
	 * Method that actually creates the file.
	 * 
	 * @param dbDialect to use
	 */
	private void generate(Dialect dialect) {
		
		Map<String, String> settings = new HashMap<>();
		settings.put("hibernate.dialect",  dialect.getDialectClass());
		
		ServiceRegistry serviceRegistry = 
			      new StandardServiceRegistryBuilder().applySettings(settings).build();
		
		// Determine file name. Use "ddl_" plus dialect name such as mysql or
		// oracle plus the package name with "_" replacing "." such as
		// org_transitime_db_structs .
		String packeNameSuffix = 
				packageName.replace(".", "_");
		String outputFilename = (outputDirectory!=null?outputDirectory+"/" : "") + 
				"ddl_" + dialect.name().toLowerCase() + 
				"_" + packeNameSuffix + ".sql";
		
		// Export, but only to an SQL file. Don't actually modify the database
		System.out.println("Writing file " + outputFilename);		
		
		MetadataSources metadatasource = new MetadataSources(serviceRegistry);
							
		for(Class<Object> annotatedClass:classList)
		{
			metadatasource.addAnnotatedClass( annotatedClass);
		}
		
		Metadata metadata =metadatasource.buildMetadata();
		
	    new SchemaExport().setDelimiter(";") //
	            .setOutputFile(outputFilename)
	            .create(EnumSet.of(TargetType.SCRIPT), metadata);
	 
	    metadata.buildSessionFactory().close();
		
		// Get rid of unneeded SQL for dropping tables and keys and such
		trimCruftFromFile(outputFilename);
	}

	/**
	 * Utility method used to fetch Class list based on a package name.
	 * 
	 * @param packageName
	 *            (should be the package containing your annotated beans.
	 */
	@SuppressWarnings("rawtypes")
	private List<Class> getClasses(String packageName) throws Exception {
	    
	    logger.debug("Start: Classes in "+ packageName);
	    List<Class> classes = new ArrayList<Class>();
	    final ClassLoader loader = Thread.currentThread().getContextClassLoader();

        for (final ClassPath.ClassInfo info : ClassPath.from(loader).getTopLevelClasses()) 
        {
            if (info.getName().startsWith(packageName)) 
            {
                final Class<?> clazz = info.load();
                logger.debug(info.getName());
                classes.add(clazz);
            } 
        }
        logger.debug("End: Classes in "+ packageName);
	   
        
		return classes;
	}

	/**
	 * Holds the class names of hibernate dialects for easy reference.
	 */
	private static enum Dialect {
		ORACLE("org.hibernate.dialect.Oracle10gDialect"), 
		// Note that using special ImprovedMySqlDialect
		MYSQL("org.transitclock.applications.SchemaGenerator$ImprovedMySQLDialect"),
		POSTGRES("org.hibernate.dialect.PostgreSQLDialect"),
		HSQL("org.hibernate.dialect.HSQLDialect");

		private String dialectClass;

		private Dialect(String dialectClass) {
			this.dialectClass = dialectClass;
		}

		public String getDialectClass() {
			return dialectClass;
		}
	}

	/**
	 * Param args args[0] is the package name for the Hibernate annotated
	 * classes whose schema is to be exported such as
	 * "org.transitclock.db.structs". args[1] is optional output directory where
	 * the resulting files are to go. If the optional output directory is not
	 * specified then schema files written to local directory.
	 * <p>
	 * The resulting files have the name "ddl_" plus dialect name such as mysql
	 * or oracle plus the first two components of the package name such as
	 * org_transitclock.
	 */
	public static void main(String[] args) throws Exception {
		// Handle the command line options
		CommandLineParser parser = new BasicParser();
		Options options = new Options();
		Option hibernatePackagePathOption =
				new Option(
						"p",
						"hibernatePackagePath",
						true,
						"This is the path to the package containing the "
						+ "hibernate annotated java classes");

		Option outputDirectoryOption =
				new Option("o", "outputDirectory", true,
						"This is the directory to output the sql");
		hibernatePackagePathOption.setRequired(true);
		outputDirectoryOption.setRequired(true);
		options.addOption(outputDirectoryOption);
		options.addOption(hibernatePackagePathOption);

		try {
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("p") && cmd.hasOption("o")) {
				String packageName = cmd.getOptionValue("p");
				String outputDirectory = cmd.getOptionValue("o");

				// Note: need to use separate SchemaGenerator objects for each
				// dialect because for some reason they otherwise interfere
				// with each other.
				SchemaGenerator gen =
						new SchemaGenerator(packageName, outputDirectory);
				gen.generate(Dialect.POSTGRES);

				gen = new SchemaGenerator(packageName, outputDirectory);
				gen.generate(Dialect.ORACLE);

				gen = new SchemaGenerator(packageName, outputDirectory);
				gen.generate(Dialect.MYSQL);
			} else {
				// Necessary command line options were not set
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar generateDatabaseSchema.jar", 
						options);				
				System.exit(-1);
			}
		} catch (ParseException pe) {
			logger.error(pe.getMessage());

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar generateDatabaseSchema.jar", 
					options);
			System.exit(-1);
		}
	}

}

