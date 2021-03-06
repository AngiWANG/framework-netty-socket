package com.allinpay.io.framework.netty.socket.client;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NettyTcpClient {

	private static final Logger logger = LoggerFactory.getLogger(NettyTcpClient.class);

	/**
	 * 远程服务端地址
	 */
	private String remoteServerHost;

	/**
	 * 远程服务端端口
	 */
	private int remoteServerPort;

	/**
	 * 重连时间间隔，单位：毫秒
	 * 
	 * 注：小于等于0则不需要重连
	 */
	private long reConnnectInterval = 3 * 1000L;

	private volatile EventLoopGroup workerGroup;

	private volatile Bootstrap bootstrap;

	private ChannelHandler clientChannelHandlerInitializer;

	public void close() {
		workerGroup.shutdownGracefully();
	}

	public void init() {
		workerGroup = new NioEventLoopGroup();
		bootstrap = new Bootstrap();
		bootstrap.group(workerGroup).channel(NioSocketChannel.class);

		bootstrap.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				// 定时重连
				ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
					@Override
					public void channelInactive(ChannelHandlerContext ctx) throws Exception {
						logger.error("连接被关闭" + ctx.channel().toString());
						scheduleConnect();
					}

					@Override
					public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
						logger.warn("发生异常，关闭连接" + ctx.channel().toString(), cause);
						// 断开连接
						ctx.close();
					}
				});
				ch.pipeline().addLast(clientChannelHandlerInitializer);
			}
		});
		doConnect();
	}

	private void doConnect() {
		logger.info("开始连接：" + getServerInfo());
		bootstrap.connect(new InetSocketAddress(remoteServerHost, remoteServerPort)).addListener(new ChannelFutureListener() {
			public void operationComplete(ChannelFuture f) throws Exception {
				if (f.isSuccess()) {
					InetSocketAddress localInetSocketAddress = (InetSocketAddress) f.channel().localAddress();
					logger.info("连接成功：" + localInetSocketAddress.getAddress().getHostAddress() + ":"
							+ localInetSocketAddress.getPort() + " -> " + getServerInfo());
				} else {
					logger.error("连接失败：" + getServerInfo() + "，原因：" + f.cause());
					// 启动连接失败时定时重连
					scheduleConnect();
				}
			}
		});
	}

	/**
	 * 定时重连
	 */
	private void scheduleConnect() {
		if (reConnnectInterval > 0) {
			workerGroup.schedule(new Runnable() {
				@Override
				public void run() {
					doConnect();
				}
			}, reConnnectInterval, TimeUnit.MILLISECONDS);
		}
	}

	private String getServerInfo() {
		return String.format("%s:%d", remoteServerHost, remoteServerPort);
	}

	public EventLoopGroup getWorkerGroup() {
		return workerGroup;
	}

	public Bootstrap getBootstrap() {
		return bootstrap;
	}

	public String getRemoteServerHost() {
		return remoteServerHost;
	}

	public void setRemoteServerHost(String remoteServerHost) {
		this.remoteServerHost = remoteServerHost;
	}

	public int getRemoteServerPort() {
		return remoteServerPort;
	}

	public void setRemoteServerPort(int remoteServerPort) {
		this.remoteServerPort = remoteServerPort;
	}

	public long getReConnnectInterval() {
		return reConnnectInterval;
	}

	public void setReConnnectInterval(long reConnnectInterval) {
		this.reConnnectInterval = reConnnectInterval;
	}

	public ChannelHandler getClientChannelHandlerInitializer() {
		return clientChannelHandlerInitializer;
	}

	public void setClientChannelHandlerInitializer(ChannelHandler clientChannelHandlerInitializer) {
		this.clientChannelHandlerInitializer = clientChannelHandlerInitializer;
	}

}
