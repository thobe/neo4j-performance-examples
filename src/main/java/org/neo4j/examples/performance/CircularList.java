package org.neo4j.examples.performance;

import java.io.IOException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.impl.batchinsert.BatchInserter;
import org.neo4j.kernel.impl.batchinsert.BatchInserterImpl;

public class CircularList extends ConfiguredExample
{
    public static void main( String[] args )
    {
        new MainMethod( CircularList.class ).dispatch( args );
    }

    @MainMethod.Entry
    public static void create( String length ) throws IOException
    {
        final long start = System.currentTimeMillis();

        new CircularList().create( Integer.parseInt( length ) );

        System.out.printf( "Inserted %s nodes and %s relationships in %.3f seconds%n", length,
                length, ( System.currentTimeMillis() - start ) / 1000.0 );
    }

    @MainMethod.Entry
    public static void traverse( String runs, String seconds ) throws IOException
    {
        new CircularList().traverseGraph( Integer.parseInt( runs ),
                Integer.parseInt( seconds ) * 1000 );
    }

    private final String storeDir;

    private CircularList()
    {
        super( null );
        this.storeDir = getStoreDir();
    }

    enum Types implements RelationshipType
    {
        CIRCLE
    }

    private void create( int len )
    {
        BatchInserter insterter = new BatchInserterImpl( getStoreDir(), neo4jConfiguration );
        try
        {

            for ( int id = 1; id < len; id++ )
            {
                insterter.createNode( id, null );
            }

            for ( int id = 0; id < len; id++ )
            {
                insterter.createRelationship( id, ( id + 1 ) % len, Types.CIRCLE, null );
            }

        }
        finally
        {
            insterter.shutdown();
        }
    }

    private void traverseGraph( final int runCount, final int msTime )
    {
        GraphDatabaseService graphDb = new EmbeddedGraphDatabase( getStoreDir(), neo4jConfiguration );

        try
        {

            for ( int i = 0; i < runCount; i++ )
            {
                long time = System.currentTimeMillis();
                long count = traverseNode( graphDb.getReferenceNode(), msTime );
                time = System.currentTimeMillis() - time;
                System.out.printf( "Traversal speed %.3f hops/ms%n", ( (double) count )
                                                                     / ( (double) time ) );
            }

        }
        finally
        {
            graphDb.shutdown();
        }
    }

    private long traverseNode( Node node, long msTime )
    {
        final long start = System.currentTimeMillis();
        long count = 0;

        while ( System.currentTimeMillis() - start < msTime )
        {
            for ( int i = 0; i < 100; i++, count++ )
            {
                node = node.getSingleRelationship( Types.CIRCLE, Direction.OUTGOING ).getEndNode();
            }
        }

        return count;
    }
}
