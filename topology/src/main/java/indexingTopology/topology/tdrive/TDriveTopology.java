package indexingTopology.topology.tdrive;

import indexingTopology.api.client.GeoTemporalQueryClient;
import indexingTopology.api.client.GeoTemporalQueryRequest;
import indexingTopology.api.client.QueryResponse;
import indexingTopology.bolt.*;
import indexingTopology.common.aggregator.AggregateField;
import indexingTopology.common.aggregator.Aggregator;
import indexingTopology.common.aggregator.Count;
import indexingTopology.common.data.DataSchema;
import indexingTopology.config.TopologyConfig;
import indexingTopology.topology.TopologyGenerator;
import indexingTopology.util.taxi.City;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.generated.StormTopology;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Created by acelzj on 6/19/17.
 */
public class TDriveTopology {

    /**
     * general configuration
     */
    @Option(name = "--help", aliases = {"-h"}, usage = "help")
    private boolean Help = false;

    @Option(name = "--mode", aliases = {"-m"}, usage = "submit|ingest|query")
    private String Mode = "Not Given";

    /**
     * topology configuration
     */
    @Option(name = "--topology-name", aliases = "-t", usage = "topology name")
    private String TopologyName = "T0";

    @Option(name = "--node", aliases = {"-n"}, usage = "number of nodes used in the topology")
    private int NumberOfNodes = 1;

    @Option(name = "--local", usage = "run the topology in local cluster")
    private boolean LocalMode = false;

    /**
     * ingest api configuration
     */

    @Option(name = "--input-file-path", aliases = {"-f"}, usage = "the input file path")
    private String InputFilePath = "";

    @Option(name = "--ingest-rate-limit", aliases = {"-r"}, usage = "max ingestion rate")
    private int MaxIngestRate = Integer.MAX_VALUE;

    /**
     * query api configuration
     */
    @Option(name = "--query-server-ip", usage = "the query server ip")
    private String QueryServerIp = "localhost";

    @Option(name = "--selectivity", usage = "the selectivity on the key domain")
    private double Selectivity = 1;

    @Option(name = "--temporal", usage = "recent time in seconds of interest")
    private int RecentSecondsOfInterest = 5;

    @Option(name = "--queries", usage = "number of queries to perform")
    private int NumberOfQueries = Integer.MAX_VALUE;


    static final double x1 = 116.0;
    static final double x2 = 117.0;
    static final double y1 = 39.6;
    static final double y2 = 40.6;
    final int partitions = 128;

    public void executeQuery() {

        double selectivityOnOneDimension = Math.sqrt(Selectivity);
        DataSchema schema = getDataSchema();
        GeoTemporalQueryClient queryClient = new GeoTemporalQueryClient(QueryServerIp, 10001);
        Thread queryThread = new Thread(() -> {
            try {
                queryClient.connectWithTimeout(10000);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            Random random = new Random();

            int executed = 0;
            long totalQueryTime = 0;

            while (true) {

                double x = x1 + (x2 - x1) * (1 - selectivityOnOneDimension) * random.nextDouble();
                double y = y1 + (y2 - y1) * (1 - selectivityOnOneDimension) * random.nextDouble();

                final double xLow = x;
                final double xHigh = x + selectivityOnOneDimension * (x2 - x1);
                final double yLow = y;
                final double yHigh = y + selectivityOnOneDimension * (y2 - y1);

                System.out.println("xlow " + xLow);
                System.out.println("xhigh " + xHigh);
                System.out.println("ylow " + yLow);
                System.out.println("yhigh " + yHigh);

//                DataTuplePredicate predicate = t ->
//                                 (double) schema.getValue("lon", t) >= xLow &&
//                                (double) schema.getValue("lon", t) <= xHigh &&
//                                (double) schema.getValue("lat", t) >= yLow &&
//                                (double) schema.getValue("lat", t) <= yHigh ;

                final int id = new Random().nextInt(100000);
                final String idString = "" + id;
//                DataTuplePredicate predicate = t -> schema.getValue("id", t).equals(Integer.toString(new Random().nextInt(100000)));
//                DataTuplePredicate predicate = t -> schema.getValue("id", t).equals(idString);



                Aggregator<Integer> aggregator = new Aggregator<>(schema, null, new AggregateField(new Count(), "*"));
//                Aggregator<Integer> aggregator = null;


//                DataSchema schemaAfterAggregation = aggregator.getOutputDataSchema();
//                DataTupleSorter sorter = (DataTuple o1, DataTuple o2) -> Double.compare((double) schemaAfterAggregation.getValue("count(*)", o1),
//                        (double) schemaAfterAggregation.getValue("count(*)", o2));


//                DataTupleEquivalentPredicateHint equivalentPredicateHint = new DataTupleEquivalentPredicateHint("id", idString);

                GeoTemporalQueryRequest queryRequest = new GeoTemporalQueryRequest<>(xLow, xHigh, yLow, yHigh,
                        System.currentTimeMillis() - RecentSecondsOfInterest * 1000,
                        System.currentTimeMillis(), null, null, aggregator, null, null);
                long start = System.currentTimeMillis();
                try {
                    DateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
                    Calendar cal = Calendar.getInstance();
                    System.out.println("[" + dateFormat.format(cal.getTime()) + "]: A query will be issued.");
                    QueryResponse response = queryClient.query(queryRequest);
                    System.out.println("A query finished.");
                    System.out.println("B1");
                    long end = System.currentTimeMillis();
                    totalQueryTime += end - start;
                    System.out.println(response);
                    System.out.println(String.format("Query time: %d ms", end - start));
                    System.out.println("B2");

                    if (executed++ >= NumberOfQueries) {
                        System.out.println("Average Query Latency: " + totalQueryTime / (double)executed);
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    Thread.interrupted();
                } catch (IOException e) {
                    if (Thread.currentThread().interrupted()) {
                        Thread.interrupted();
                    }
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
            try {
                queryClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        queryThread.start();
    }

    public void submitTopology() throws InvalidTopologyException, AuthorizationException, AlreadyAliveException {
        DataSchema schema = getDataSchema();
        City city = new City(x1, x2, y1, y2, partitions);

        Integer lowerBound = 0;
        Integer upperBound = city.getMaxZCode();
        System.out.println("upperBound: " + upperBound);

        final boolean enableLoadBalance = true;

        TopologyConfig config = new TopologyConfig();

//        InputStreamReceiver dataSource = new InputStreamReceiverServer(rawSchema, 10000, config);

        InputStreamReceiverBolt dataSource = new TDriveDataSourceBolt(schema, city, config, InputFilePath, MaxIngestRate);

        QueryCoordinatorBolt<Integer> queryCoordinatorBolt = new GeoTemporalQueryCoordinatorBoltBolt<>(lowerBound,
                upperBound, 10001, city, config, schema);

//        DataTupleMapper dataTupleMapper = new DataTupleMapper(rawSchema, (Serializable & Function<DataTuple, DataTuple>) t -> {
//            double lon = (double)schema.getValue("lon", t);
//            double lat = (double)schema.getValue("lat", t);
//            int zcode = city.getZCodeForALocation(lon, lat);
//            t.add(zcode);
//            t.add(System.currentTimeMillis());
//            return t;
//        });

        List<String> bloomFilterColumns = new ArrayList<>();
        bloomFilterColumns.add("id");

        TopologyGenerator<Integer> topologyGenerator = new TopologyGenerator<>();
        topologyGenerator.setNumberOfNodes(NumberOfNodes);

        StormTopology topology = topologyGenerator.generateIndexingTopology(schema, lowerBound, upperBound,
                enableLoadBalance, dataSource, queryCoordinatorBolt, null, bloomFilterColumns, config);

        Config conf = new Config();
        conf.setDebug(false);
        conf.setNumWorkers(NumberOfNodes);

        conf.put(Config.WORKER_CHILDOPTS, "-Xmx2048m");
        conf.put(Config.WORKER_HEAP_MEMORY_MB, 2048);

        if (LocalMode) {
            LocalCluster localCluster = new LocalCluster();
            localCluster.submitTopology(TopologyName, conf, topology);
        } else {
            StormSubmitter.submitTopology(TopologyName, conf, topology);
        }
    }

    public static void main(String[] args) throws InvalidTopologyException, AuthorizationException, AlreadyAliveException {

        TDriveTopology tDriveTopology = new TDriveTopology();

        CmdLineParser parser = new CmdLineParser(tDriveTopology);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            e.printStackTrace();
            parser.printUsage(System.out);
        }

        if (tDriveTopology.Help) {
            parser.printUsage(System.out);
        }

        switch (tDriveTopology.Mode) {
            case "submit": tDriveTopology.submitTopology(); break;
            case "query": tDriveTopology.executeQuery(); break;
            default: System.out.println("Invalid command!");
        }
//        if (command.equals("submit"))
//            submitTopology();
//        else if (command.equals("ingest"))
//            executeIngestion();
//        else if (command.equals("query"))
//            executeQuery();

    }


    static private DataSchema getRawDataSchema() {
        DataSchema rawSchema = new DataSchema();
        rawSchema.addIntField("id");
        rawSchema.addIntField("zcode");
        rawSchema.addDoubleField("longitude");
        rawSchema.addDoubleField("latitude");
        return rawSchema;
    }

    static private DataSchema getDataSchema() {
        DataSchema schema = new DataSchema();
        schema.addIntField("id");
        schema.addIntField("zcode");
        schema.addDoubleField("longitude");
        schema.addDoubleField("latitude");
        schema.addLongField("timestamp");
        schema.setPrimaryIndexField("zcode");
        return schema;
    }
}
