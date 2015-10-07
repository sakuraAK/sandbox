package siri.rest.service.resources;

import siri.rest.service.model.SiriMessage;
import java.util.HashMap;
import java.util.Map;


public class DataProvider {

	private static DataProvider instance;
	
	private Map<String, SiriMessage> messages;
	
	private DataProvider()
	{
		this.messages = new HashMap<String, SiriMessage>();
		//add some data for testing
		SiriMessage msg = new SiriMessage("1", "123", "20", "F", "10000", "10100");
		this.messages.put(msg.getMsgId(), msg);
		msg = new SiriMessage("2", "124", "25", "F", "10000", "10100");
		this.messages.put(msg.getMsgId(), msg);
	}
	
	public static DataProvider getInstance()
	{
		if(instance == null)
			instance = new DataProvider();
		return instance;
	}
	
	synchronized public SiriMessage getMessage(String id)
	{
		SiriMessage msg = null;
		if(this.messages.containsKey(id))
			msg = this.messages.get(id);
		
		return msg;
			
	}
	
	public Map<String, SiriMessage> getModel(String org)
	{
		return this.messages;
	}
}
