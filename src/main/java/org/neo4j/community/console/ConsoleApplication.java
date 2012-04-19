package org.neo4j.community.console;

import com.google.gson.Gson;
import org.neo4j.geoff.except.SubgraphError;
import org.neo4j.geoff.except.SyntaxError;
import spark.Request;
import spark.Response;
import spark.servlet.SparkApplication;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Scanner;

import static org.neo4j.helpers.collection.MapUtil.map;
import static spark.Spark.*;

public class ConsoleApplication implements SparkApplication {

    private static final String DEFAULT_GRAPH = "(Neo) {\"name\": \"Neo\"} (Morpheus) {\"name\": \"Morpheus\"} " +
            "(Trinity) {\"name\": \"Trinity\"} (Cypher) {\"name\": \"Cypher\"} (Smith) {\"name\": \"Agent Smith\"} " +
            "(Architect) {\"name\":\"The Architect\"} (0)-[:ROOT]->(Neo) (Neo)-[:KNOWS]->(Morpheus) " +
            "(Neo)-[:LOVES]->(Trinity) (Morpheus)-[:KNOWS]->(Trinity) (Morpheus)-[:KNOWS]->(Cypher) " +
            "(Cypher)-[:KNOWS]->(Smith) (Smith)-[:CODED_BY]->(Architect)";
    private static final String DEFAULT_QUERY = "start n=node(*) match n-[r?]->m return n,type(r),m";

    @Override
    public void init() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                SessionHoldingListener.cleanSessions();
                System.gc();
            }
        });

        post(new Route("/console/cypher") {
            protected Object doHandle(Request request, Response response, Neo4jService service) {
                final String query = request.body();
                return service.cypherQuery(query);
            }
        });
        get(new Route("/console/cypher") {
            protected Object doHandle(Request request, Response response, Neo4jService service) {
                String query = request.queryParams("query");
                return new Gson().toJson(service.cypherQueryResults(query));
            }

        });
        post(new Route("/console/init") {
            @Override
            protected void doBefore(Request request, Response response) {
                reset(request);
            }

            protected Object doHandle(Request request, Response response, Neo4jService service) {
                final Map input = new Gson().fromJson(request.body(),Map.class);
                String init = param(input, "init", DEFAULT_GRAPH);
                String query = param(input, "query", DEFAULT_QUERY);
                final Map<String, Object> data = map("init", init, "query", query);

                long start = System.currentTimeMillis(), time = start;
                try {
                    time = trace("service", time);
                    data.put("geoff", service.mergeGeoff(init));
                    time = trace("geoff", time);
                    data.put("result", service.cypherQuery(query));
                    time = trace("cypher", time);
                    data.put("visualization", service.cypherQueryViz(query));
                    trace("viz", time);
                } catch (Exception e) {
                    data.put("error", e.getMessage());
                }
                time = trace("all", start);
                data.put("time", time);
                return new Gson().toJson(data);
            }

        });
        get(new Route("/console/visualization") {
            protected Object doHandle(Request request, Response response, Neo4jService service) {
                String query = request.queryParams("query");
                return new Gson().toJson(service.cypherQueryViz(query));
            }
        });
        get(new Route("/console/to_geoff") {
            protected Object doHandle(Request request, Response response, Neo4jService service) {
                return service.toGeoff();
            }
        });
        get(new Route("/console/url") {
            protected Object doHandle(Request request, Response response, Neo4jService service) throws IOException {
                final String uri = "http://console.neo4j.org?init=" + URLEncoder.encode(service.toGeoff(), "UTF-8");
				return shortenUrl(uri);
            	}
        });
        get(new Route("/console/shorten") {
            protected Object doHandle(Request request, Response response, Neo4jService service) throws IOException {
                return shortenUrl(request.queryParams("url"));
            }
        });

        delete(new Route("/console") {
            protected Object doHandle(Request request, Response response, Neo4jService service) {
                reset(request);
                return "deleted";
            }
        });

        post(new Route("/console/geoff") {
            protected Object doHandle(Request request, Response response, Neo4jService service) throws SyntaxError, SubgraphError {
                Map res = service.mergeGeoff(request.body());
                return new Gson().toJson(res);
            }
        });
    }

    private String shortenUrl(String uri) throws IOException {
        final InputStream stream = (InputStream) new URL("http://tinyurl.com/api-create.php?url=" + URLEncoder.encode(uri, "UTF-8")).getContent();
        final String shortUrl = new Scanner(stream).useDelimiter("\\z").next();
        stream.close();
        return shortUrl;
	}
}
