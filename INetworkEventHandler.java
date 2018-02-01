import javafx.scene.paint.Color;
import java.util.UUID;

/**
 * Implementations of this interface can recieve network events from the Network subsystem.
 *
 * @see Network
 *
 * @author John Vidler
 */
public interface INetworkEventHandler {
    /**
     * Called by a Network object internally whenever a text message is received.
     *
     * @param message The message that was received, as a String.
     */
    void textMessage( String message );

    /**
     * Called by a Network object whenever a remote client updates a some ball data.
     *
     * @link Network#sendBallUpdate(Ball)
     *
     * @see Network
     * @See Ball
     *
     * @param uuid The (reasonably) unique identifier for the remote Ball object - should be network-wide-unique.
     * @param x The X position of this Ball
     * @param y The Y position of this Ball
     * @param color The color of this Ball
     */
    void updateBall( UUID uuid, double x, double y, double d, Color color );

    /**
     * Called by a Network object whenever a remote client updates some rectangle data.
     *
     * @link Network#sendRectangleUpdate(Rectangle)
     *
     * @see Network
     * @see Rectangle
     *
     * @param uuid The (reasonably) unique identifier for the remote Rectangle object - should be network-wide-unique.
     * @param x The X position of this Rectangle
     * @param y The Y position of this Rectangle
     * @param w The width of this Rectangle
     * @param h The height of this Rectangle
     * @param color The color of this Rectangle
     */
    void updateRectangle( UUID uuid, double x, double y, double w, double h, Color color );
}
