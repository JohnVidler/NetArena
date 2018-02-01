import javafx.scene.paint.Color;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * @author John Vidler
 */
public class Network {
    private final static String MULTICAST_ADDRESS = "225.4.5.6";
    private final static int   DEFAULT_PORT       = 5000;
    private final static int   MAX_TEXT_LENGTH    = 128;

    private final static byte HEARTBEAT    = 0;
    private final static byte UPDATE_BALL  = 1;
    private final static byte UPDATE_RECT  = 2;
    private final static byte TEXT_MESSAGE = 10;

    public final UUID networkID = UUID.randomUUID();
    private DatagramChannel networkChannel = null;
    private List<INetworkEventHandler>   eventHandlers    = new ArrayList<>();
    private int networkKey = 0;

    /**
     * Create a new network handling object
     *
     * <b>THERE SHOULD ONLY BE ONE OF THESE PER APPLICATION!</b> otherwise it'll get very, very confusing.
     *
     * To recieve events from the network, clients should implement/instantiate INetworkEventHandler and add themselves
     * as a listener.
     *
     * @see INetworkEventHandler
     */
    public Network(){
        try {
            System.out.println( "Connecting to p2p network..." );
            networkChannel = connectToNetwork();

            Thread networkThread = new Thread(networkWorker);
            networkThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setNetworkKey( int key ) {
        networkKey = key;
        System.out.println( "Joined network #"+key );
    }

    private DatagramChannel connectToNetwork() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while( interfaces.hasMoreElements() ) {
            NetworkInterface iface = interfaces.nextElement();
            if( iface.isUp() && iface.supportsMulticast() && !iface.isLoopback() ) {
                System.out.println( "Using network interface: " + iface.getDisplayName() );
                DatagramChannel dc = DatagramChannel.open( StandardProtocolFamily.INET )
                        .setOption( StandardSocketOptions.SO_REUSEADDR, true )
                        .bind( new InetSocketAddress( DEFAULT_PORT ) )
                        .setOption( StandardSocketOptions.IP_MULTICAST_IF, iface );

                InetAddress group = InetAddress.getByName( MULTICAST_ADDRESS );

                MembershipKey key = dc.join( group, iface );

                return dc;
            }
        }

        return null;
    }

    private Runnable networkWorker = () -> {
        System.out.println( "Network worker thread started!" );

        // Reused for all messages, should be as long as the longest message
        ByteBuffer rxBuffer = ByteBuffer.allocate( 128 );
        while( networkChannel != null ) {
            try {
                rxBuffer.clear();
                SocketAddress from = networkChannel.receive( rxBuffer );

                if( rxBuffer.position() == 0 ) {
                    System.out.println( "Zero-length packet!" );
                    continue;
                }

                synchronized ( Network.this ) {
                    if (from != null) {
                        //System.out.printf( "<%s>\t%s\n", from, toHexString( rxBuffer ) );

                        if( rxBuffer.getInt( 0 ) == networkKey ) {
                            switch (rxBuffer.get(4)) {
                                case HEARTBEAT:
                                    break;

                                case TEXT_MESSAGE:
                                    parseTextMessage(rxBuffer);
                                    break;

                                case UPDATE_BALL:
                                    parseBallUpdate(rxBuffer);
                                    break;

                                case UPDATE_RECT:
                                    parseRectangleUpdate(rxBuffer);
                                    break;

                                default:
                                    System.err.printf("Bad message type (%d)! Are you running different versions of the network code?", rxBuffer.get(0));
                            }
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println( "Network worker thread stopped!" );
    };

    private String toHexString( ByteBuffer buffer ) {
        StringBuffer hexBuffer = new StringBuffer();
        for( int i=0; i<buffer.limit(); i++ )
            hexBuffer.append( Integer.toHexString(buffer.get(i) & 0xFF) ).append( " " );
        return hexBuffer.toString();
    }

    /**
     * Send the parameters of a Ball over the network link to <b>ALL</b> clients.
     *
     * Receivers will get the parameters in a updateBall event.
     *
     * @link INetworkEventHandler#updateBall(UUID,double,double,Color)
     *
     * @see INetworkEventHandler
     *
     * @param ball The ball object to pull data from.
     */
    public void sendBallUpdate( Ball ball ) {
        ByteBuffer buffer = ByteBuffer.allocate( 128 );
        buffer.position( 0 ); // Probably not required, but just to be safe...
        buffer.putInt( networkKey );
        buffer.put( UPDATE_BALL );
        buffer.putLong( ball.uuid.getMostSignificantBits() );
        buffer.putLong( ball.uuid.getLeastSignificantBits() );

        buffer.putDouble( ball.getXPosition() );
        buffer.putDouble( ball.getYPosition() );
        buffer.putDouble( ball.getSize() );

        Color color = Color.web( ball.getColour() ); // In a 'real' network application, this should be cached in the ball object.
        buffer.putDouble( color.getRed() );
        buffer.putDouble( color.getGreen() );
        buffer.putDouble( color.getBlue() );  // Note: Only support RGB colors, ignore brightness/saturation.

        buffer.limit( buffer.position() );
        buffer.position( 0 );

        try {
            networkChannel.send( buffer, new InetSocketAddress( MULTICAST_ADDRESS, DEFAULT_PORT ) );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseBallUpdate( ByteBuffer buffer ) {
        buffer.position( 5 ); // Discard the type byte

        // Calculate the UUID for this update
        UUID remoteUUID = new UUID( buffer.getLong(), buffer.getLong() );

        // Update the values with any new data
        double x    = buffer.getDouble();
        double y    = buffer.getDouble();
        double d    = buffer.getDouble();
        Color color = Color.color( buffer.getDouble(), buffer.getDouble(), buffer.getDouble() );

        emitBallUpdate( remoteUUID, x, y, d, color );
    }

    /**
     * Send the parameters of a Rectangle over the network link to <b>ALL</b> clients.
     *
     * Receivers will get the parameters in a updateRectangle event.
     *
     * @link INetworkEventHandler#updateRectangle(UUID,double,double,double,double,Color)
     *
     * @see INetworkEventHandler
     *
     * @param rectangle The Rectangle object to pull data from.
     */
    public void sendRectangleUpdate( Rectangle rectangle ) {
        ByteBuffer buffer = ByteBuffer.allocate( 128 );
        buffer.position( 0 ); // Probably not required, but just to be safe...
        buffer.putInt( networkKey );
        buffer.put( UPDATE_RECT );
        buffer.putLong( rectangle.uuid.getMostSignificantBits() );
        buffer.putLong( rectangle.uuid.getLeastSignificantBits() );

        buffer.putDouble( rectangle.getXPosition() );
        buffer.putDouble( rectangle.getYPosition() );
        buffer.putDouble( rectangle.getWidth() );
        buffer.putDouble( rectangle.getHeight() );

        Color color = Color.web( rectangle.getColour() ); // In a 'real' network application, this should be cached in the ball object.
        buffer.putDouble( color.getRed() );
        buffer.putDouble( color.getGreen() );
        buffer.putDouble( color.getBlue() );  // Note: Only support RGB colors, ignore brightness/saturation.

        buffer.limit( buffer.position() );
        buffer.position( 0 );

        try {
            networkChannel.send( buffer, new InetSocketAddress( MULTICAST_ADDRESS, DEFAULT_PORT ) );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseRectangleUpdate( ByteBuffer buffer ) {
        buffer.position( 5 ); // Discard the type byte

        // Calculate the UUID for this update
        UUID remoteUUID = new UUID( buffer.getLong(), buffer.getLong() );

        // Update the values with any new data
        double x    = buffer.getDouble();
        double y    = buffer.getDouble();
        double w    = buffer.getDouble();
        double h    = buffer.getDouble();
        Color color = Color.color( buffer.getDouble(), buffer.getDouble(), buffer.getDouble() );

        emitRectangleUpdate( remoteUUID, x, y, w, h, color );
    }

    /**
     * Sends a plain text message to <b>ALL</b> clients.
     *
     *
     *
     * @param message
     */
    public void sendTextMessage( String message ) {
        if( message.length() > MAX_TEXT_LENGTH - 5 )
            message = message.substring( 0, MAX_TEXT_LENGTH - 5 );

        ByteBuffer buffer = ByteBuffer.allocate( MAX_TEXT_LENGTH );
        buffer.position( 0 ); // Probably not required, but just to be safe...
        buffer.putInt( networkKey );
        buffer.put( TEXT_MESSAGE );
        buffer.putInt( message.length()-1 );
        for( int i=0; i<message.length()-1; i++ )
            buffer.putChar(message.charAt(i));

        buffer.limit( buffer.position() );
        buffer.position( 0 );

        try {
            networkChannel.send( buffer, new InetSocketAddress( MULTICAST_ADDRESS, DEFAULT_PORT ) );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseTextMessage( ByteBuffer buffer ) {
        buffer.position( 5 );

        StringBuffer outBuffer = new StringBuffer();
        int length = buffer.getInt();
        for( int i=0; i<length; i++ )
            outBuffer.append( buffer.getChar() );

        emitTextMessage( outBuffer.toString() );
    }

    public void addListener( INetworkEventHandler handler ) {
        synchronized ( eventHandlers ) {
            if (!eventHandlers.contains(handler))
                eventHandlers.add(handler);
        }
    }

    public void removeListener( INetworkEventHandler handler ) {
        synchronized ( eventHandlers ) {
            if (eventHandlers.contains(handler))
                eventHandlers.remove(handler);
        }
    }

    public void emitTextMessage( String message ) {
        synchronized ( eventHandlers ) {
            eventHandlers.forEach((iNetworkEventHandler -> {
                try {
                    iNetworkEventHandler.textMessage( message );
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }));
        }
    }

    public void emitBallUpdate( UUID uuid, double x, double y, double d, Color color ) {
        synchronized ( eventHandlers ) {
            eventHandlers.forEach((iNetworkEventHandler -> {
                try {
                    iNetworkEventHandler.updateBall( uuid, x, y, d, color );
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }));
        }
    }

    public void emitRectangleUpdate( UUID uuid, double x, double y, double w, double h, Color color ) {
        synchronized ( eventHandlers ) {
            eventHandlers.forEach((iNetworkEventHandler -> {
                try {
                    iNetworkEventHandler.updateRectangle( uuid, x, y, w, h, color );
                } catch (Exception err) {
                    err.printStackTrace();
                }
            }));
        }
    }
}
