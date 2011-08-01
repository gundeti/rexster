package com.tinkerpop.rexster;

import com.tinkerpop.blueprints.pgm.Edge;
import com.tinkerpop.blueprints.pgm.Graph;
import com.tinkerpop.blueprints.pgm.Vertex;
import com.tinkerpop.blueprints.pgm.util.json.JSONWriter;
import com.tinkerpop.rexster.extension.ExtensionMethod;
import com.tinkerpop.rexster.extension.ExtensionPoint;
import com.tinkerpop.rexster.extension.ExtensionResponse;
import com.tinkerpop.rexster.extension.ExtensionSegmentSet;
import com.tinkerpop.rexster.extension.HttpMethod;
import com.tinkerpop.rexster.extension.RexsterExtension;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;

@Path("/{graphname}/edges")
public class EdgeResource extends AbstractSubResource {

    private static Logger logger = Logger.getLogger(EdgeResource.class);

    public EdgeResource() {
        super(null);
    }

    public EdgeResource(UriInfo ui, HttpServletRequest req, RexsterApplicationProvider rap) {
        super(rap);
        this.httpServletRequest = req;
        this.uriInfo = ui;
    }

    /**
     * GET http://host/graph/edges
     * graph.getEdges();
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEdges(@PathParam("graphname") String graphName) {

        RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);

        Long start = this.getStartOffset();
        Long end = this.getEndOffset();
        boolean wasInSection = false;
        long counter = 0l;
        try {
            JSONArray edgeArray = new JSONArray();
            for (Edge edge : rag.getGraph().getEdges()) {
                if (counter >= start && counter < end) {
                    wasInSection = true;
                    edgeArray.put(JSONWriter.createJSONElement(edge, this.getReturnKeys(), this.hasShowTypes()));
                } else if (wasInSection) {
                    break;
                }
                counter++;
            }

            this.resultObject.put(Tokens.RESULTS, edgeArray);
            this.resultObject.put(Tokens.TOTAL_SIZE, edgeArray.length());
            this.resultObject.put(Tokens.QUERY_TIME, this.sh.stopWatch());

        } catch (JSONException ex) {
            logger.error(ex);

            JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();

    }

    /**
     * GET http://host/graph/edges/id
     * graph.getEdge(id);
     */
    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSingleEdge(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        final Edge edge = this.getRexsterApplicationGraph(graphName).getGraph().getEdge(id);

        if (null != edge) {
            try {
                this.resultObject.put(Tokens.RESULTS, JSONWriter.createJSONElement(edge, this.getReturnKeys(), this.hasShowTypes()));
                this.resultObject.put(Tokens.QUERY_TIME, this.sh.stopWatch());

                JSONArray extensionsList = getExtensionHypermedia(graphName, ExtensionPoint.EDGE);
                if (extensionsList != null) {
                    this.resultObject.put(Tokens.EXTENSIONS, extensionsList);
                }
            } catch (JSONException ex) {
                logger.error(ex);

                JSONObject error = generateErrorObjectJsonFail(ex);
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }
        } else {
            String msg = "Could not find edge [" + id + "] on graph [" + this.getRexsterApplicationGraph(graphName).getGraphName() + "]";
            logger.info(msg);

            JSONObject error = generateErrorObject(msg);
            throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    @HEAD
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response headVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.executeEdgeExtension(graphName, id, HttpMethod.HEAD);
    }

    @HEAD
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response headEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeEdgeExtension(graphName, id, HttpMethod.HEAD);
    }

    @HEAD
    @Path("/{id}/{extension: .+}")
    public Response headEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.HEAD);
    }

    @PUT
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response putVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.executeEdgeExtension(graphName, id, HttpMethod.PUT);
    }

    @PUT
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeEdgeExtension(graphName, id, HttpMethod.PUT);
    }

    @PUT
    @Path("/{id}/{extension: .+}")
    public Response putEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.PUT);
    }

    @OPTIONS
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response optionsVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.executeEdgeExtension(graphName, id, HttpMethod.OPTIONS);
    }

    @OPTIONS
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response optionsEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeEdgeExtension(graphName, id, HttpMethod.OPTIONS);
    }

    @OPTIONS
    @Path("/{id}/{extension: .+}")
    public Response optionsEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.OPTIONS);
    }

    @DELETE
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response deleteVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.executeEdgeExtension(graphName, id, HttpMethod.DELETE);
    }

    @DELETE
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeEdgeExtension(graphName, id, HttpMethod.DELETE);
    }

    @DELETE
    @Path("/{id}/{extension: .+}")
    public Response deleteEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.DELETE);
    }

    @POST
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.executeEdgeExtension(graphName, id, HttpMethod.POST);
    }

    @POST
    @Path("/{id}/{extension: .+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeEdgeExtension(graphName, id, HttpMethod.POST);
    }

    @POST
    @Path("/{id}/{extension: .+}")
    public Response postEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.POST);
    }

    @GET
    @Path("/{id}/{extension: .+}")
    public Response getEdgeExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeEdgeExtension(graphName, id, HttpMethod.GET);
    }

    private Response executeEdgeExtension(String graphName, String id, HttpMethod httpMethodRequested) {

        final Edge edge = this.getRexsterApplicationGraph(graphName).getGraph().getEdge(id);

        ExtensionResponse extResponse;
        ExtensionMethod methodToCall;
        ExtensionSegmentSet extensionSegmentSet = parseUriForExtensionSegment(graphName, ExtensionPoint.EDGE);

        // determine if the namespace and extension are enabled for this graph
        RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);

        if (rag.isExtensionAllowed(extensionSegmentSet)) {

            Object returnValue = null;

            // namespace was allowed so try to run the extension
            try {

                // look for the extension as loaded through serviceloader
                List<RexsterExtension> rexsterExtensions = null;
                try {
                    rexsterExtensions = findExtensionClasses(extensionSegmentSet);
                } catch (ServiceConfigurationError sce) {
                    logger.error("ServiceLoader could not find a class referenced in com.tinkerpop.rexster.extension.RexsterExtension.");
                    JSONObject error = generateErrorObject(
                            "Class specified in com.tinkerpop.rexster.extension.RexsterExtension could not be found.",
                            sce);
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                if (rexsterExtensions == null && rexsterExtensions.size() == 0) {
                    // extension was not found for some reason
                    logger.error("The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "].  Check com.tinkerpop.rexster.extension.RexsterExtension file in META-INF.services.");
                    JSONObject error = generateErrorObject(
                            "The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "]");
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                // look up the method on the extension that needs to be called.
                methodToCall = findExtensionMethod(rexsterExtensions, ExtensionPoint.EDGE, extensionSegmentSet.getExtensionMethod(), httpMethodRequested);

                if (methodToCall == null) {
                    // extension method was not found for some reason
                    logger.error("The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "] with a HTTP method of [" + httpMethodRequested.name() + "].  Check com.tinkerpop.rexster.extension.RexsterExtension file in META-INF.services.");
                    JSONObject error = generateErrorObject(
                            "The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "] with a HTTP method of [" + httpMethodRequested.name() + "]");
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                // found the method...time to do work
                returnValue = invokeExtension(graphName, methodToCall, edge);

            } catch (WebApplicationException wae) {
                // already logged this...just throw it  up.
                throw wae;
            } catch (Exception ex) {
                logger.error("Dynamic invocation of the [" + extensionSegmentSet + "] extension failed.", ex);
                JSONObject error = generateErrorObjectJsonFail(ex);
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }

            if (returnValue instanceof ExtensionResponse) {
                extResponse = (ExtensionResponse) returnValue;

                if (extResponse.isErrorResponse()) {
                    // an error was raised within the extension.  pass it back out as an error.
                    logger.warn("The [" + extensionSegmentSet + "] extension raised an error response.");
                    throw new WebApplicationException(Response.fromResponse(extResponse.getJerseyResponse()).build());
                }
            } else {
                // extension method is not returning the correct type...needs to be an ExtensionResponse
                logger.error("The [" + extensionSegmentSet + "] extension does not return an ExtensionResponse.");
                JSONObject error = generateErrorObject(
                        "The [" + extensionSegmentSet + "] extension does not return an ExtensionResponse.");
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }

        } else {
            // namespace was not allowed
            logger.error("The [" + extensionSegmentSet + "] extension was not configured for [" + graphName + "]");
            JSONObject error = generateErrorObject(
                    "The [" + extensionSegmentSet + "] extension was not configured for [" + graphName + "]");
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        String mediaType = MediaType.APPLICATION_JSON;
        if (methodToCall != null) {
            mediaType = methodToCall.getExtensionDefinition().produces();
            extResponse = tryAppendRexsterAttributesIfJson(extResponse, methodToCall, mediaType);
        }

        return Response.fromResponse(extResponse.getJerseyResponse()).type(mediaType).build();
    }

    /**
     * POST http://host/graph/edges
     * graph.addEdge(null);
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postNullEdge(@PathParam("graphname") String graphName, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.postEdge(graphName, null);
    }

    /**
     * POST http://host/graph/edges
     * graph.addEdge(null);
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postNullEdge(@PathParam("graphname") String graphName, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        return this.postEdge(graphName, null);
    }

    /**
     * POST http://host/graph/edge?_inV=id1&_outV=id2&label=string&key=value
     * Edge e = graph.addEdge(null,graph.getVertex(id1),graph.getVertex(id2),label);
     * e.setProperty(key,value);
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response postNullEdge(@PathParam("graphname") String graphName) {
        return this.postEdge(graphName, null);
    }

    /**
     * POST http://host/graph/edge/id
     * Edge e = graph.addEdge(id,graph.getVertex(id1),graph.getVertex(id2),label);
     * e.setProperty(key,value);
     */
    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postEdge(@PathParam("graphname") String graphName, @PathParam("id") String id, MultivaluedMap<String, String> formParams) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.buildRequestObject(formParams);
        return this.postEdge(graphName, id);
    }

    /**
     * POST http://host/graph/edge/id
     * Edge e = graph.addEdge(id,graph.getVertex(id1),graph.getVertex(id2),label);
     * e.setProperty(key,value);
     */
    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postEdge(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        return this.postEdge(graphName, id);
    }

    /**
     * POST http://host/graph/edge/id?_inV=id1&_outV=id2&label=string&key=value
     * Edge e = graph.addEdge(id,graph.getVertex(id1),graph.getVertex(id2),label);
     * e.setProperty(key,value);
     */
    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postEdge(@PathParam("graphname") String graphName, @PathParam("id") String id) {

        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();
        String inV = null;
        Object temp = this.getRequestObject().opt(Tokens._IN_V);
        if (null != temp)
            inV = temp.toString();
        String outV = null;
        temp = this.getRequestObject().opt(Tokens._OUT_V);
        if (null != temp)
            outV = temp.toString();
        String label = null;
        temp = this.getRequestObject().opt(Tokens._LABEL);
        if (null != temp)
            label = temp.toString();

        rag.tryStartTransaction();
        try {
            Edge edge = graph.getEdge(id);
            if (null == edge && null != outV && null != inV && null != label) {
                // there is no edge but the in/out vertex params and label are present so
                // validate that the vertexes are present before creating the edge
                final Vertex out = graph.getVertex(outV);
                final Vertex in = graph.getVertex(inV);
                if (null != out && null != in) {
                    // in/out vertexes are found so edge can be created
                    edge = graph.addEdge(id, out, in, label);
                }

            } else if (edge != null) {
                if (!this.hasElementProperties(this.getRequestObject())) {
                    JSONObject error = generateErrorObjectJsonFail(new Exception("Edge with id " + id + " already exists"));
                    throw new WebApplicationException(Response.status(Status.CONFLICT).entity(error).build());
                }
            }

            try {
                if (edge != null) {
                    Iterator keys = this.getRequestObject().keys();
                    while (keys.hasNext()) {
                        String key = keys.next().toString();
                        if (!key.startsWith(Tokens.UNDERSCORE)) {
                            edge.setProperty(key, this.getTypedPropertyValue(this.getRequestObject().getString(key)));
                        }
                    }
                    this.resultObject.put(Tokens.RESULTS, JSONWriter.createJSONElement(edge, this.getReturnKeys(), this.hasShowTypes()));
                } else {
                    // edge could not be found.  likely an error condition on the request
                    JSONObject error = generateErrorObjectJsonFail(new Exception("Edge cannot be found or created.  Please check the format of the request."));
                    throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
                }

                rag.tryStopTransactionSuccess();

                this.resultObject.put(Tokens.QUERY_TIME, sh.stopWatch());
            } catch (JSONException ex) {
                logger.error(ex);

                JSONObject error = generateErrorObjectJsonFail(ex);
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }


        } catch (WebApplicationException wae) {
            rag.tryStopTransactionFailure();
            throw wae;
        } catch (Exception ex) {
            rag.tryStopTransactionFailure();
            JSONObject error = generateErrorObject("Transaction failed on POST of edge.", ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    /**
     * DELETE http://host/graph/edge/id
     * graph.removeEdge(graph.getEdge(id));
     * <p/>
     * DELETE http://host/graph/edge/id?key1&key2
     * Edge e = graph.getEdge(id);
     * e.removeProperty(key1);
     * e.removeProperty(key2);
     */
    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEdge(@PathParam("graphname") String graphName, @PathParam("id") String id) {

        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();

        try {
            rag.tryStartTransaction();
            final List<String> keys = this.getNonRexsterRequestKeys();
            final Edge edge = graph.getEdge(id);
            if (null != edge) {
                if (keys.size() > 0) {
                    // delete edge properties
                    for (final String key : keys) {
                        edge.removeProperty(key);
                    }
                } else {
                    // delete edge
                    graph.removeEdge(edge);
                }
            } else {
                String msg = "Could not find edge [" + id + "] on graph [" + this.getRexsterApplicationGraph(graphName).getGraphName() + "]";
                logger.info(msg);
                JSONObject error = generateErrorObject(msg);
                throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
            }

            rag.tryStopTransactionSuccess();

            this.resultObject.put(Tokens.QUERY_TIME, sh.stopWatch());
        } catch (JSONException ex) {
            logger.error(ex);

            rag.tryStopTransactionFailure();

            JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (WebApplicationException wae) {
            rag.tryStopTransactionFailure();
            throw wae;
        } catch (Exception ex) {
            rag.tryStopTransactionFailure();
            JSONObject error = generateErrorObject("Transaction failed on DELETE of edge.", ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();

    }
}
