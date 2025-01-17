package org.bok.core.net;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Striped;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.bok.common.overlay.server.Channel;
import org.bok.common.overlay.server.MessageQueue;
import org.bok.consensus.base.Param;
import org.bok.consensus.pbft.PbftManager;
import org.bok.consensus.pbft.message.PbftBaseMessage;
import org.bok.consensus.pbft.message.PbftMessage;
import org.bok.core.exception.P2pException;
import org.bok.core.net.peer.PeerConnection;

@Component
@Scope("prototype")
public class PbftHandler extends SimpleChannelInboundHandler<PbftMessage> {

  protected PeerConnection peer;

  private MessageQueue msgQueue;

  private static final Striped<Lock> striped = Striped.lazyWeakLock(1024);

  private static final Cache<String, Boolean> msgCache = CacheBuilder.newBuilder()
      .initialCapacity(3000).maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES).build();

  @Autowired
  private PbftManager pbftManager;

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, PbftMessage msg) throws Exception {
    msgQueue.receivedMessage(msg);
    if (Param.getInstance().getPbftInterface().isSyncing()) {
      return;
    }
    msg.analyzeSignature();
    String key = buildKey(msg);
    Lock lock = striped.get(key);
    try {
      lock.lock();
      if (msgCache.getIfPresent(key) != null) {
        return;
      }
      if (!pbftManager.verifyMsg(msg)) {
        throw new P2pException(P2pException.TypeEnum.BAD_MESSAGE, msg.toString());
      }
      msgCache.put(key, true);
      pbftManager.forwardMessage(msg);
      pbftManager.doAction(msg);
    } finally {
      lock.unlock();
    }

  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    peer.processException(cause);
  }

  public void setMsgQueue(MessageQueue msgQueue) {
    this.msgQueue = msgQueue;
  }

  public void setChannel(Channel channel) {
    this.peer = (PeerConnection) channel;
  }

  private String buildKey(PbftBaseMessage msg) {
    return msg.getKey() + msg.getPbftMessage().getRawData().getMsgType().toString();
  }

}