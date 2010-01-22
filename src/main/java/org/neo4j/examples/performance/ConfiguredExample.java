package org.neo4j.examples.performance;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.neo4j.kernel.EmbeddedGraphDatabase;

public abstract class ConfiguredExample
{
    @Target( ElementType.TYPE )
    @Retention( RetentionPolicy.RUNTIME )
    public @interface StringRepresentation
    {
        IntrospectionAttribute[] value() default {};
    }

    public enum IntrospectionAttribute
    {
        GARBAGE_COLLECTORS
        {
            @Override
            String value()
            {
                List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
                String[] names = new String[collectors.size()];
                int i = 0;
                for ( GarbageCollectorMXBean gc : collectors )
                {
                    names[i++] = gc.getName();
                }
                return Arrays.toString( names );
            }
        };

        @Override
        public final String toString()
        {
            return name() + ":" + value();
        }

        abstract String value();
    }

    protected final Map<String, String> neo4jConfiguration;
    private final Properties properties;
    private final String prefix;

    protected ConfiguredExample( String neo4j_config_file )
    {
        this.prefix = getClass().getSimpleName();
        String config_file = System.getProperty( "ConfiguredExample.neo4j-config-file" );
        if ( config_file == null || config_file.trim().equals( "" ) )
        {
            config_file = neo4j_config_file;
        }
        Map<String, String> config;
        try
        {
            config = EmbeddedGraphDatabase.loadConfigurations( config_file );
        }
        catch ( Exception e )
        {
            config = new HashMap<String, String>();
        }
        this.neo4jConfiguration = config;
        String filename = this.prefix + ".properties";
        InputStream stream = null;
        try
        {
            stream = new FileInputStream( filename );
        }
        catch ( FileNotFoundException noFile )
        {
            stream = getClass().getResourceAsStream( filename );
        }
        properties = new Properties();
        if ( stream != null )
        {
            try
            {
                properties.load( stream );
            }
            catch ( Exception e )
            {
            }
        }
    }

    @Override
    public final String toString()
    {
        Class<? extends ConfiguredExample> cls = getClass();
        StringRepresentation repr = cls.getAnnotation( StringRepresentation.class );
        if ( repr == null )
        {
            return super.toString();
        }
        StringBuilder result = new StringBuilder( cls.getSimpleName() );
        result.append( '{' );
        for ( IntrospectionAttribute attr : repr.value() )
        {
            result.append( attr.toString() );
        }
        buildStringRepresentation( result );
        result.append( '}' );
        return result.toString();
    }

    protected void buildStringRepresentation( StringBuilder result )
    {
        // Override me!
    }

    protected String stringProperty( String key, String defaultValue )
    {
        return System.getProperty( prefix + "." + key, properties.getProperty(
                key, defaultValue ) );
    }

    protected int intProperty( String key, int defaultValue )
    {
        return Integer.parseInt( stringProperty( key,
                Integer.toString( defaultValue ) ) );
    }

    protected boolean booleanProperty( String key, boolean defaultValue )
    {
        return Boolean.parseBoolean( stringProperty( key,
                Boolean.toString( defaultValue ) ) );
    }

    @SuppressWarnings( "unchecked" )
    protected <E extends Enum> E enumProperty( Class<E> type, String key,
            E defaultValue )
    {
        return (E) Enum.valueOf( type,
                stringProperty( key, defaultValue.name() ) );
    }

    protected void printProgress( long current, long total, long time )
    {
        try
        {
            if ( ( current + 1 ) % ( total / 100 ) == 0 )
            {
                time = System.currentTimeMillis() - time;
                System.out.println( "" + ( ( current * 100 / total ) + 1 )
                                    + "% -- " + ( time / 1000.0 ) + "s" );
            }
        }
        catch ( Exception ex )
        {
        }
    }
}
