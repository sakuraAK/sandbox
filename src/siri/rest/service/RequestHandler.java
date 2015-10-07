package siri.rest.service;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import siri.rest.service.resources.DataProvider;
import siri.rest.service.model.*;
import java.util.ArrayList;
import java.util.List;


@Path("/getmessages")
public class RequestHandler {

	@Context
	UriInfo uriInfo;
	@Context
	Request request;
	  
	@GET
	@Path("{org}")
	@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
	public List<SiriMessage> getMessagesXML(@PathParam("org") String org)
	{
		//SiriMessage msg = new SiriMessage("1", "123", "20", "F", "10000", "10100");
		List<SiriMessage> msgs = new ArrayList<SiriMessage>();
	    msgs.addAll(DataProvider.getInstance().getModel(org).values());
		return msgs;
	}
	
	@GET
	@Path("{org}")
	@Produces({MediaType.TEXT_XML})
	public List<SiriMessage> getMessagesHTML(@PathParam("org") String org)
	{
		//SiriMessage msg = new SiriMessage("1", "123", "20", "F", "10000", "10100");
		List<SiriMessage> msgs = new ArrayList<SiriMessage>();
	    msgs.addAll(DataProvider.getInstance().getModel(org).values());
		return msgs;
	}
	
	
}
