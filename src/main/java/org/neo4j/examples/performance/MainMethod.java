package org.neo4j.examples.performance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class MainMethod
{
    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    public @interface Entry
    {
    }

    public static void main( String[] args )
    {
        new MainMethod( MainMethod.class ).dispatch( args );
    }

    private final Map<String, Method> entries;
    private final String name;

    public MainMethod( Class<?> cls )
    {
        this.name = cls.getSimpleName();
        Map<String, Method> entries = new HashMap<String, Method>();
        for ( Method method : cls.getDeclaredMethods() )
        {
            Entry entry = method.getAnnotation( Entry.class );
            if ( entry != null )
            {
                assertIsValidEntry( method );
                if ( entries.put( method.getName(), method ) != null )
                {
                    throw new IllegalArgumentException(
                            cls.getName()
                                    + " has multiple entry methods called "
                                    + method.getName() );
                }
            }
        }
        if ( entries.isEmpty() )
        {
            throw new IllegalArgumentException( cls.getName()
                                                + " has no entry methods." );
        }
        this.entries = entries;
    }

    private static void assertIsValidEntry( Method method )
    {
        if ( ( method.getModifiers() & Modifier.STATIC ) == 0 )
        {
            throw new IllegalArgumentException();
        }
        Class<?>[] params = method.getParameterTypes();
        if ( params.length > 1 )
        {
            throw new IllegalArgumentException();
        }
        else if ( params.length == 1 )
        {
            Class<?> param = params[0];
            if ( !param.equals( String.class )
                 && !param.equals( String[].class ) )
            {
                throw new IllegalArgumentException();
            }
        }
    }

    public void dispatch( String[] args )
    {
        String method = null;
        if ( args.length > 0 && entries.containsKey( args[0] ) )
        {
            method = args[0];
            String[] newargs = new String[args.length - 1];
            System.arraycopy( args, 1, newargs, 0, newargs.length );
            args = newargs;
        }
        else
        {
            method = System.getProperty( getClass().getSimpleName()
                                         + ".dispatch" );
        }
        Method entry = entries.get( method );
        if ( entry == null )
        {
            System.out.println( "Please specify one of the "
                                + "following valid entry points:" );
            for ( String name : entries.keySet() )
            {
                System.out.println( name );
            }
            return;
        }
        try
        {
            if ( entry.getParameterTypes().length == 0 )
            {
                entry.invoke( null );
            }
            else if ( entry.getParameterTypes()[0].equals( String[].class ) )
            {
                entry.invoke( null, ( (Object) args ) );
            }
            else
            {
                entry.invoke( null, (Object[]) args );
            }
        }
        catch ( InvocationTargetException e )
        {
            e.getCause().printStackTrace();
        }
        catch ( IllegalArgumentException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch ( IllegalAccessException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
