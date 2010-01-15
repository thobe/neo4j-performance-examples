package org.neo4j.examples.performance;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.neo4j.kernel.EmbeddedGraphDatabase;

public abstract class ConfiguredExample
{
    protected final Map<String, String> neo4jConfiguration;
    private final Properties properties;
    private final String prefix;

    protected ConfiguredExample( String neo4j_config_file )
    {
        this.prefix = getClass().getSimpleName();
        String config_file = System
            .getProperty( "ConfiguredExample.neo4j-config-file" );
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

    protected String stringProperty( String key, String defaultValue )
    {
        return System.getProperty( prefix + key, properties.getProperty( key,
            defaultValue ) );
    }

    protected int intProperty( String key, int defaultValue )
    {
        return Integer.parseInt( stringProperty( key, Integer
            .toString( defaultValue ) ) );
    }

    protected boolean booleanProperty( String key, boolean defaultValue )
    {
        return Boolean.parseBoolean( stringProperty( key, Boolean
            .toString( defaultValue ) ) );
    }

    @SuppressWarnings( "unchecked" )
    protected <E extends Enum> E enumProperty( Class<E> type, String key,
        E defaultValue )
    {
        return ( E ) Enum.valueOf( type, stringProperty( key, defaultValue
            .name() ) );
    }

    protected void printProgress( long current, long total, long time )
    {
        if ( ( current + 1 ) % ( total / 100 ) == 0 )
        {
            time = System.currentTimeMillis() - time;
            System.out.println( "" + ( ( current * 100 / total ) + 1 )
                + "% -- " + ( time / 1000.0 ) + "s" );
        }
    }
}
