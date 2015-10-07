package siri.rest.service.model;

import javax.xml.bind.annotation.*;


@XmlRootElement
public class SiriMessage {

	private String msgId;
	private String stopId;
	private String lineNumber;
	private String direction;
	private String eta1;
	private String eta2;
	
	
	public SiriMessage(String id, String stopid, String line, String direction, String eta1, String eta2)
	{
		this.msgId = id;
		this.stopId = stopid;
		this.lineNumber = line;
		this.direction = direction;
		this.eta1 = eta1;
		this.eta2 = eta2;
	}
	
	public SiriMessage()
	{
		
	}
	
	public String getMsgId() {
		return msgId;
	}
	
	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}
	
	public String getStopId() {
		return stopId;
	}
	
	public void setStopId(String stopId) {
		this.stopId = stopId;
	}
	
	public String getLineNumber() {
		return lineNumber;
	}
	
	public void setLineNumber(String lineNumber) {
		this.lineNumber = lineNumber;
	}
	
	public String getDirection() {
		return direction;
	}
	
	public void setDirection(String direction) {
		this.direction = direction;
	}
	
	public String getEta1() {
		return eta1;
	}
	
	public void setEta1(String eta1) {
		this.eta1 = eta1;
	}
	
	public String getEta2() {
		return eta2;
	}
	
	public void setEta2(String eta2) {
		this.eta2 = eta2;
	}
}
