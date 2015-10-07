package siri.rest.service;
import siri.rest.service.model.*;
import siri.rest.service.resources.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;


public class MessageResource {

	@Context
	  UriInfo uriInfo;
	  @Context
	  Request request;
	  String id;
	  public MessageResource(UriInfo uriInfo, Request request, String id) {
	    this.uriInfo = uriInfo;
	    this.request = request;
	    this.id = id;
	  }
	  
	  //Application integration     
	  @GET
	  @Path("{org}")
	  @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
	  public SiriMessage getMessage(@PathParam("org") String org) {
	    SiriMessage msg = DataProvider.getInstance().getModel(org).get(id);
	    if(msg==null)
	      throw new RuntimeException("Get: Message with " + id +  " not found");
	    return msg;
	  }
	  
	  // for the browser
	  @GET
	  @Path("{org}")
	  @Produces(MediaType.TEXT_XML)
	  public SiriMessage getTodoHTML(@PathParam("org") String org) {
		  SiriMessage msg = DataProvider.getInstance().getModel(org).get(id);
		  if(msg==null)
		     throw new RuntimeException("Get: Message with " + id +  " not found");
		  return msg;
	  }
}
