package org.transitclock.custom.aws;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.transitclock.db.structs.AvlReport;
import org.transitclock.db.structs.AvlReport.AssignmentType;

import com.amazonaws.services.sqs.model.Message;
import org.transitclock.db.structs.OccupancyStatus;

/**
 * Implementation of SqsMessageUnmarshaller for WMATA data. 
 *
 */
public class WmataAvlTypeUnmarshaller implements SqsMessageUnmarshaller {

  private static final Logger logger = 
      LoggerFactory.getLogger(WmataAvlTypeUnmarshaller.class);
  
  @Override
  public List<AvlReportWrapper> toAvlReports(Message message) {
    List<AvlReportWrapper> reports = new ArrayList<AvlReportWrapper>();
    String body = message.getBody();

    if (body.startsWith("[")) {
      reports.addAll(toAvlReportsFromJsonArray(body));
    } else if (body.contains("SigningCertURL")) {
      // SNS to SQS leaves some artifacts
      // TODO find out why this happens
      try {
          JSONObject jsonObj = new JSONObject(message.getBody());
          String content = jsonObj.getString("Message");
          reports.addAll(toAvlReportsFromJsonArray(content));    
      } catch (JSONException e) {
          // try one last time
        reports.add(toAvlReport(message));
      }
    }
    else {
      // not an array, try to deserialize as is
      reports.add(toAvlReport(message));
    }
    return reports;
  }

  
  private List<AvlReportWrapper> toAvlReportsFromJsonArray(String body) {
    List<AvlReportWrapper> reports = new ArrayList<AvlReportWrapper>();
    if (body == null) return reports;
    // we have an array
    JSONArray jsonArray = new JSONArray(body);
    for (int i=0; i<jsonArray.length(); i++) {
      reports.add(toAvlReport((JSONObject)jsonArray.get(i)));
    }
    return reports;
  }


  private AvlReportWrapper toAvlReport(JSONObject jsonObj) {
    JSONObject msgObj;
    try{
        msgObj = new JSONObject(jsonObj.getString("Message"));
    }catch(JSONException e){
        msgObj = jsonObj;  //un
    }

    String vehicleId = null;
    if (msgObj.has("vehicleid"))
        vehicleId = String.valueOf(msgObj.getLong("vehicleid"));
    Double lat = null;
    if (msgObj.has("latitude"))
        lat = msgObj.getDouble("latitude");

    Double lon = null;
    if (msgObj.has("longitude"))
        lon = msgObj.getDouble("longitude");

    Long time = null;
    if (msgObj.has("avlDate"))
        time = msgObj.getLong("avlDate");

    Float heading = null;
    if (msgObj.has("heading")) {
        heading = (float) msgObj.getDouble("heading");
    }

    Float speed = null;
    // incoming speed is mph according to NextBus ICD Rev 1.3.pdf, but it guessing its actually ft/s
    if (msgObj.has("averageSpeed")) {
        speed = (float) msgObj.getDouble("averageSpeed") * 0.3048f; // convert to m/s
    }

    OccupancyStatus occupancyStatus = null;
    if (msgObj.has("crowdingStatus")) {
        String occupancyString = null;
        occupancyString = msgObj.getString("crowdingStatus");
        occupancyStatus = OccupancyStatus.lenientParse(occupancyString);
    }

    Long forwarderTimeReceived = null;
    if (msgObj.has("received")) {
      forwarderTimeReceived = msgObj.getLong("received");
    }
    
    Long forwarderTimeProcessed = null;
    if (msgObj.has("processed")) {
      forwarderTimeProcessed = msgObj.getLong("processed");
    }
    
    Long sqsLatency = null;
    Long avlLatency = null;
    Long totalLatency = null;
    Long forwarderProcessingLatency = null;
    if (forwarderTimeReceived != null) {
      Long now = System.currentTimeMillis();
      
      if (time != null) {
        totalLatency = now - time;
        avlLatency = forwarderTimeReceived - time;
        if (forwarderTimeProcessed != null) {
          forwarderProcessingLatency = forwarderTimeProcessed - forwarderTimeReceived;
          sqsLatency = now - forwarderTimeProcessed;
        }
      }
      
    }
    
    String source = "sqs";
    if (vehicleId != null && lat != null && lon != null && time != null) {
        AvlReport ar = new AvlReport(vehicleId, time, lat, lon, speed, heading, source);
        if (occupancyStatus != null) {
            ar.setOccupancyStatus(occupancyStatus);
        }
        if (msgObj.has("blockAlpha")) {
            String blockAlpha = msgObj.getString("blockAlpha");
            if (blockAlpha != null) {
                ar.setAssignment(blockAlpha, AssignmentType.BLOCK_ID);
            }
        }
        return new AvlReportWrapper(ar, avlLatency, forwarderProcessingLatency, sqsLatency, totalLatency);
    }
    logger.info("invalid message={}", jsonObj);
    // missing necessary info
    return null;
    
  }
  
    @Override
    public AvlReportWrapper toAvlReport(Message message) {
        if (message == null || message.getBody() == null) return null;
        JSONObject jsonObj = new JSONObject(message.getBody());
        return toAvlReport(jsonObj);
    }

    @Override
    public String toString(Message message) {
        if (message == null) return null;
        return message.getBody();
    }

}
