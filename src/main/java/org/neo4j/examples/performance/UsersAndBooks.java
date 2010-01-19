package org.neo4j.examples.performance;

import java.io.IOException;
import java.util.Iterator;
import java.util.Random;

import org.neo4j.commons.iterator.FilteringIterator;
import org.neo4j.commons.iterator.IteratorWrapper;
import org.neo4j.commons.iterator.NestingIterator;
import org.neo4j.examples.performance.ConfiguredExample.IntrospectionAttribute;
import org.neo4j.examples.performance.ConfiguredExample.StringRepresentation;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.impl.batchinsert.BatchInserter;
import org.neo4j.kernel.impl.batchinsert.BatchInserterImpl;

@StringRepresentation( IntrospectionAttribute.GARBAGE_COLLECTORS )
public class UsersAndBooks extends ConfiguredExample
{
    public static void main( String[] args )
    {
        new MainMethod( UsersAndBooks.class ).dispatch( args );
    }

    @MainMethod.Entry
    public static void create() throws IOException
    {
        new UsersAndBooks( "create.properties" ).createGraph();
    }

    @MainMethod.Entry
    public static void traverse() throws IOException
    {
        new UsersAndBooks( "traverse.properties" ).traverseGraph();
    }

    private static final int MILLION = 1000000;

    private static double expectedValue( int min, int max )
    {
        int sum = 0;
        for ( int number = min; number < max; number++ )
        {
            sum += number;
        }
        return ( ( double ) sum ) / ( ( double ) ( max - min ) );
    }

    private enum Execution
    {
        MIN, MAX, EXPECTED,
    }

    private final Random random = new Random();
    private final RelationshipType FAVORITE = DynamicRelationshipType
        .withName( "FAVORITE" );
    private final String storeDir;
    private final int numBooks;
    private final int numUsers;
    private final boolean renderProgression;
    private final int numTraversals;
    private final boolean optimistic;

    private UsersAndBooks( String neo4j_config_file )
    {
        super( neo4j_config_file );
        this.storeDir = stringProperty( "storeDir", "target/neodb" );
        int BOOKS = intProperty( "numBooks", 1 * MILLION );
        int USERS = intProperty( "numUsers", 100 * MILLION );
        int MIN_FAVORITES = intProperty( "minFavorites", 100 );
        int MAX_FAVORITES = intProperty( "maxFavorites", 1000 );
        double FAVORITES;
        Execution kind = enumProperty( Execution.class, "kind",
            Execution.EXPECTED );
        switch ( kind )
        {
            case MIN:
                FAVORITES = MIN_FAVORITES;
                break;
            case MAX:
                FAVORITES = MAX_FAVORITES;
                break;
            case EXPECTED:
            default:
                FAVORITES = expectedValue( MIN_FAVORITES, MAX_FAVORITES );
        }
        double FAVORITED = USERS * FAVORITES / BOOKS;
        this.numBooks = ( int ) Math.ceil( FAVORITES );
        this.numUsers = ( int ) Math.ceil( FAVORITED );
        this.renderProgression = booleanProperty( "renderProgression", true );
        this.numTraversals = intProperty( "numTraversals", 2 );
        this.optimistic = booleanProperty( "optimistic", false );
        // Print out statistics about the started instance
        System.out.println( "Users and Books traversal example, " + kind.name()
            + " type " + ( this.optimistic ? "optimistic " : " " )
            + "graph.\nEach user has " + numBooks
            + " favorite books and each book is the favorite of " + numUsers
            + " users." );
    }

    @Override
    protected void printProgress( long current, long total, long time )
    {
        if ( renderProgression )
        {
            super.printProgress( current, total, time );
        }
    }

    private void createGraph()
    {
        BatchInserter batch = new BatchInserterImpl( storeDir,
            neo4jConfiguration );
        try
        {
            create( batch );
        }
        finally
        {
            batch.shutdown();
        }
    }

    private void traverseGraph()
    {
        GraphDatabaseService graphdb = new EmbeddedGraphDatabase( storeDir,
            neo4jConfiguration );
        try
        {
            for ( int i = 0; i < numTraversals; i++ )
            {
                traverse( graphdb );
            }
        }
        finally
        {
            graphdb.shutdown();
        }
    }

    private long bookId( int book )
    {
        return 1 + book;
    }

    private long userId( int user )
    {
        return 1 + numBooks + user;
    }

    private void create( BatchInserter batch )
    {
        // Create nodes
        int total = numBooks + numBooks * numUsers;
        System.out.println( "Inserting nodes..." );
        long start = System.currentTimeMillis();
        for ( int nodeid = 1; nodeid <= total; nodeid++ )
        {
            batch.createNode( nodeid, null );
            printProgress( nodeid, total, start );
        }
        long time = System.currentTimeMillis() - start;
        System.out.println( "Inserted " + total + " nodes in "
            + ( time / 1000.0 ) + " seconds.\nThat is "
            + ( ( ( double ) total ) / ( ( double ) time ) )
            + " nodes per millisecond." );

        // Create relationships
        total = numBooks * numUsers;
        System.out.println( "Inserting relationships..." );
        start = System.currentTimeMillis();
        for ( int book = 0, count = 0; book < numBooks; book++ )
        {
            /* Reference nodes in semi-random order as to emulate a structure
             * of the relationships being created incrementally, which is what
             * we would see in an actual application. This means that when we
             * traverse the relationships from any given nodes they will not
             * have consecutive IDs, and thus be scattered across the
             * relationship store, reflecting the state it would be in if
             * created incrementally from actual usage.
             */
            // FIXME: this might not be correct...
            int offset = random.nextInt( numUsers );
            for ( int user = 0; user < numUsers; user++, count++ )
            {
                if ( ( optimistic && user == 0 )
                    || ( !optimistic && user == offset ) )
                {
                    batch
                        .createRelationship( 0, bookId( book ), FAVORITE, null );
                }
                batch
                    .createRelationship( userId( user ),
                        bookId( optimistic ? book
                            : ( ( offset + book ) % numBooks ) ), FAVORITE,
                        null );
                printProgress( count, total, start );
            }
        }
        time = System.currentTimeMillis() - start;
        System.out.println( "Inserted " + ( total + numBooks )
            + " relationships in " + ( time / 1000.0 ) + " seconds.\nThat is "
            + ( ( ( double ) ( total + numBooks ) ) / ( ( double ) time ) )
            + " relationships per millisecond." );
    }

    private void traverse( GraphDatabaseService graphdb )
    {
        int count = 0;
        int total = numBooks * numUsers;
        System.out.println( "Traversing graph" );
        long time = System.currentTimeMillis();
        Transaction tx = graphdb.beginTx();
        try
        {
            for ( Node node : iterator( graphdb.getReferenceNode() ) )
            {
                printProgress( count++, total, time );
            }
        }
        finally
        {
            tx.finish();
        }
        time = System.currentTimeMillis() - time;
        System.out.println( "Counted " + count + " (of " + total
            + ") users in " + ( time / 1000.0 ) + " seconds." );
        total += numBooks;
        System.out.println( "Traversed " + total + " relationships in "
            + ( time / 1000.0 ) + " seconds.\nThat is "
            + ( ( ( double ) total ) / ( ( double ) time ) )
            + " relationships per millisecond." );
    }

    private Iterable<Node> iterator( final Node start )
    {
        /* Don't use the traverser framework, since it keeps a collection of
         * visited nodes. For this traversal the memory overhead of that would
         * require a too large heap and have a negative performance impact on
         * the traversal. In part because it limits the portion of the heap
         * available to be used by the Neo4j cache, and in part because the
         * overhead of finding matches in the collection will grow
         * unnecessarily as the size of it grows since there will never be any
         * hits in the collection (except for the start node, and that is
         * simple to check for by hand) due to how the data is constructed.
         */
        return new Iterable<Node>()
        {
            public Iterator<Node> iterator()
            {
                return new NestingIterator<Node, Relationship>( start
                    .getRelationships( FAVORITE ).iterator() )
                {
                    @Override
                    protected Iterator<Node> createNestedIterator(
                        Relationship item )
                    {
                        final Node node = item.getOtherNode( start );
                        Iterator<Node> iter = new IteratorWrapper<Node, Relationship>(
                            node.getRelationships( FAVORITE ).iterator() )
                        {
                            @Override
                            protected Node underlyingObjectToObject(
                                Relationship object )
                            {
                                return object.getOtherNode( node );
                            }
                        };
                        return new FilteringIterator<Node>( iter )
                        {
                            @Override
                            protected boolean passes( Node user )
                            {
                                return !user.equals( start );
                            }
                        };
                    }
                };
            }
        };
    }
}
