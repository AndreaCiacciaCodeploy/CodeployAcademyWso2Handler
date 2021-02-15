package com.wso2.customhandler.logtrace;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.rest.RESTConstants;

/**
 * Handler wso2esb Generiamo e salviamo traceId per poter tracciare tutti i
 * passaggi di una chiamata in arrivo su esb
 * 
 * @author andrea.ciaccia
 *
 */
public class MessageEsbHandler extends AbstractSynapseHandler 
{
	private static final Log LOGGER = LogFactory.getLog(MessageEsbHandler.class);
	private static final String INFLOW_REQUEST_START_TIME = "INFLOW_REQUEST_START_TIME";
	private static final String OUTFLOW_REQUEST_START_TIME = "OUTFLOW_REQUEST_START_TIME";
	private static final String INFLOW_RESPONSE_END_TIME = "INFLOW_RESPONSE_END_TIME";

	/**
	 * Incoming request to the service or API. 
	 * This is where we will determine the tracking id
	 * and log HTTP method and headers similar to wire log.
	 */
	public boolean handleRequestInFlow(MessageContext synCtx) {
		SynapseLog log = LogTrackUtil.getLog(synCtx, LOGGER);
		log.auditDebug("init handleRequestInFlow");
		synCtx.setProperty(INFLOW_REQUEST_START_TIME, System.currentTimeMillis());
		try {
			// Set logging context
			LogTrackUtil.setLogContext(synCtx, log);

			StringBuilder msg = new StringBuilder();
			msg.append(">> ");
			msg.append(LogTrackUtil.getHTTPMethod(synCtx));
			msg.append(" ");
			msg.append(LogTrackUtil.getToHTTPAddress(synCtx));

			log.auditLog(msg);
			log.auditLog(">> HTTP Headers " + LogTrackUtil.getHTTPHeaders(synCtx));
			log.auditLog(">> HTTP Request Payload " + LogTrackUtil.getHTTPRequestPayload(synCtx));
			log.auditDebug("end handleRequestInFlow");
		} catch (Exception e) {
			log.auditWarn("handleRequestInFlow Unable to set log context due to : " + e.getMessage());
		}
		return true; 
	}

	/**
	 * Outgoing request from the service to the backend. 
	 * This is where we will log the outgoing HTTP address and headers.
	 */
	public boolean handleRequestOutFlow(MessageContext synCtx) {
		SynapseLog log = LogTrackUtil.getLog(synCtx, LOGGER);
		log.auditDebug("init handleRequestOutFlow");
		synCtx.setProperty(OUTFLOW_REQUEST_START_TIME, System.currentTimeMillis());

		try {
			// Set the logging context
			LogTrackUtil.setLogContext(synCtx, log);
			
			StringBuilder msg = new StringBuilder();
			msg.append(">>>> ");
			msg.append(LogTrackUtil.getHTTPMethod(synCtx));
			msg.append(" ");
			msg.append(LogTrackUtil.getToHTTPAddress(synCtx));
			
			log.auditLog(msg);			
			log.auditLog(">>>> HTTP Headers " + LogTrackUtil.getHTTPHeaders(synCtx));
			log.auditLog(">>>> HTTP Request Payload " + LogTrackUtil.getHTTPRequestPayload(synCtx));
			log.auditDebug("end handleRequestOutFlow");
		} catch (Exception e) {
			log.auditWarn("handleRequestOutFlow Unable to set log context due to : " + e.getMessage());
		}
		return true; 
	}

	/**
	 * Incoming response from backend to service. 
	 * This is where we will log the backend response headers and status.
	 */
	public boolean handleResponseInFlow(MessageContext synCtx) {
		SynapseLog log = LogTrackUtil.getLog(synCtx, LOGGER);
		synCtx.setProperty(INFLOW_RESPONSE_END_TIME, System.currentTimeMillis());
		log.auditDebug("Entering SESynapseLogHandler.handleResponseInFlow");
		try {
			// Set the logging context
			LogTrackUtil.setLogContext(synCtx, log);
						
			log.auditLog("<<<< " + LogTrackUtil.getHTTPStatusMessage(synCtx));
			log.auditLog("<<<< HTTP Headers " + LogTrackUtil.getHTTPHeaders(synCtx));
			log.auditDebug("end handleResponseInFlow");
		} catch (Exception e) {
			log.auditWarn("handleResponseInFlow Unable to set log context due to : " + e.getMessage());
		}
		return true;
	}

	/**
	 * Outgoing response from the service to caller. 
	 * This is where we will log the service response header and status.
	 */
	public boolean handleResponseOutFlow(MessageContext synCtx) {
		SynapseLog log = LogTrackUtil.getLog(synCtx, LOGGER);
		log.auditDebug("init handleResponseOutFlow");
		long responseTime, serviceTime = 0, backendTime = 0, backendEndTime = 0;
		long endTime = System.currentTimeMillis();
		try {
			// Set the logging context
			LogTrackUtil.setLogContext(synCtx, log);
			// Log the request as per wire log
			log.auditLog("<< " + LogTrackUtil.getHTTPStatusMessage(synCtx));
			log.auditLog("<< HTTP Headers " + LogTrackUtil.getHTTPHeaders(synCtx));
			log.auditLog("return handleResponseOutFlow");

			long startTime = 0, backendStartTime = 0;
			if (synCtx.getProperty(INFLOW_REQUEST_START_TIME) != null) {
				startTime = (Long) synCtx.getProperty(INFLOW_REQUEST_START_TIME);
			}
			if (synCtx.getProperty(OUTFLOW_REQUEST_START_TIME) != null) {
				backendStartTime = (Long) synCtx.getProperty(OUTFLOW_REQUEST_START_TIME);
			}
			if (synCtx.getProperty(INFLOW_RESPONSE_END_TIME) != null) {
				backendEndTime = (Long) synCtx.getProperty(INFLOW_RESPONSE_END_TIME);
			}
			responseTime = endTime - startTime;
			// When start time not properly set
			if (startTime == 0) {
				backendTime = 0;
				serviceTime = 0;
			} else if (endTime != 0 && backendStartTime != 0 && backendEndTime != 0) { // When
				// response caching is disabled
				backendTime = backendEndTime - backendStartTime;
				serviceTime = responseTime - backendTime;
			} else if (endTime != 0 && backendStartTime == 0) {// When response caching enabled
				backendTime = 0;
				serviceTime = responseTime;
			}

			String API_NAME = (String) synCtx.getProperty(RESTConstants.SYNAPSE_REST_API);
			String HTTP_METHOD = (String) synCtx.getProperty(RESTConstants.REST_METHOD);
			String CONTEXT = (String) synCtx.getProperty(RESTConstants.REST_API_CONTEXT);
			String FULL_REQUEST_PATH = (String) synCtx.getProperty(RESTConstants.REST_FULL_REQUEST_PATH);
			String SUB_PATH = (String) synCtx.getProperty(RESTConstants.REST_SUB_REQUEST_PATH);
			String HTTP_RESPONSE_STATUS_CODE = LogTrackUtil.getHTTPStatusMessage(synCtx);
			String ERROR_CODE = String.valueOf(synCtx.getProperty(SynapseConstants.ERROR_CODE));
			String ERROR_MESSAGE = (String) synCtx.getProperty(SynapseConstants.ERROR_MESSAGE);

			
			log.auditLog("API Transaction Details:" 
					+ "API_NAME: " + API_NAME 
					+ ",HTTP_METHOD: " + HTTP_METHOD
					+ ", CONTEXT: " + CONTEXT 
					+ ",FULL_REQUEST_PATH" + FULL_REQUEST_PATH 
					+ ",SUB_PATH: " + SUB_PATH
					+ ", HTTP_RESPONSE_STATUS_CODE: " + HTTP_RESPONSE_STATUS_CODE 
					+ ", RESPONSE_TIME: " + responseTime
					+ ", BACKEND_TIME: " + backendTime 
					+ ", SERVICE_TIME: " + serviceTime 
					+ ", ERROR_CODE: " + ERROR_CODE 
					+ ", ERROR_MESSAGE: " + ERROR_MESSAGE);
			 
			LogTrackUtil.clearLogContext();
			
			
		} catch (Exception e) {
			log.auditWarn("handleResponseOutFlow Unable to set log context due to : " + e.getMessage());
		}

		return true;
	}
}
