package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.IOException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

/**
 * Main netty packet encoder. Writes the packet ID as a VarInt based on the current {@link ConnectionProtocol} as well
 * as the packet's data.
 */
public class PacketEncoder extends MessageToByteEncoder<Packet<?>> {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final Marker MARKER = MarkerManager.getMarker("PACKET_SENT", Connection.PACKET_MARKER);
   private final PacketFlow flow;

   public PacketEncoder(PacketFlow pFlow) {
      this.flow = pFlow;
   }

   protected void encode(ChannelHandlerContext p_130545_, Packet<?> p_130546_, ByteBuf p_130547_) throws Exception {
      ConnectionProtocol connectionprotocol = p_130545_.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
      if (connectionprotocol == null) {
         throw new RuntimeException("ConnectionProtocol unknown: " + p_130546_);
      } else {
         Integer integer = connectionprotocol.getPacketId(this.flow, p_130546_);
         if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MARKER, "OUT: [{}:{}] {}", p_130545_.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get(), integer, p_130546_.getClass().getName());
         }

         if (integer == null) {
            throw new IOException("Can't serialize unregistered packet");
         } else {
            FriendlyByteBuf friendlybytebuf = new FriendlyByteBuf(p_130547_);
            friendlybytebuf.writeVarInt(integer);

            try {
               int i = friendlybytebuf.writerIndex();
               p_130546_.write(friendlybytebuf);
               int j = friendlybytebuf.writerIndex() - i;
               if (j > 8388608) {
                  throw new IllegalArgumentException("Packet too big (is " + j + ", should be less than 8388608): " + p_130546_);
               } else {
                  int k = p_130545_.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get().getId();
                  JvmProfiler.INSTANCE.onPacketSent(k, integer, p_130545_.channel().remoteAddress(), j);
               }
            } catch (Throwable throwable) {
               LOGGER.error("Error encoding packet", throwable); // Forge: Get Minecraft to spit out more information about the Throwable we got.
               if (p_130546_.isSkippable()) {
                  throw new SkipPacketException(throwable);
               } else {
                  throw throwable;
               }
            }
         }
      }
   }
}
