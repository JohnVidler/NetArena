import javafx.scene.paint.Color;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Connects to the network group and waits for updates from remote peers.
 *
 * @author John Vidler
 */
public class NetworkSync {
    public static void main( String args[] ) {
        Network network = new Network();
        GameArena arena = new GameArena( 800, 600, 1234 );
        Ball localBall = new Ball(100.0, 100.0, 5.0, "BLUE" );

        Map<UUID,Ball> remoteBalls = new TreeMap<>();
        arena.addBall( localBall );

        // Handle network events
        network.addListener(new INetworkEventHandler() {
            @Override
            public void textMessage(String message) {
                System.out.println( "Message: " +message );
            }

            @Override
            public void updateBall(UUID uuid, double x, double y, double d, Color color) {
                if( localBall.uuid.compareTo(uuid) == 0 )
                    return;

                if( !remoteBalls.containsKey(uuid) ) {
                    Ball remote = new Ball( x, y, d, "YELLOW" );
                    remoteBalls.put(uuid, remote);
                    arena.addBall( remote );
                }

                Ball remote = remoteBalls.get(uuid);
                remote.setXPosition( x );
                remote.setYPosition( y );
            }

            @Override
            public void updateRectangle(UUID uuid, double x, double y, double w, double h, Color color) {
                /* skip */
            }
        });

        network.sendTextMessage( "Hello from connection " +network.networkID );

        while( true ) {
            if( arena.leftPressed() ) {
                localBall.setXPosition( localBall.getXPosition() - 3.0 );
                network.sendBallUpdate( localBall );
            }

            if( arena.rightPressed() ) {
                localBall.setXPosition( localBall.getXPosition() + 3.0 );
                network.sendBallUpdate( localBall );
            }

            if( arena.upPressed() ) {
                localBall.setYPosition( localBall.getYPosition() - 3.0 );
                network.sendBallUpdate( localBall );
            }

            if( arena.downPressed() ) {
                localBall.setYPosition( localBall.getYPosition() + 3.0 );
                network.sendBallUpdate( localBall );
            }

            arena.pause();
        }
    }
}
