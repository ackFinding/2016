package io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A server provides to download resource
 */
public class LocalServer {
	// 服务器根路径
	private final String rootIndex = "G:\\ServerSite";
	// 创建线程池
	private ExecutorService pool = new ThreadPoolExecutor(2, 4, 3,
			TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));
	// 端口号
	private final int PORT = 8001;

	public void start() {
		System.out.println("服务器开始启动");
		try (ServerSocket server = new ServerSocket(PORT)) {
			while (true) {
				Socket socket = server.accept();
				pool.submit(() -> processTask(socket));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			pool.shutdown();
		}
	}

	private void processTask(Socket clientSocket) {

		try (Socket socket = clientSocket;
				InputStream input = socket.getInputStream();
				PushbackInputStream pushBackStream = new PushbackInputStream(
						input, 8 * 1024);
				OutputStream output = socket.getOutputStream()) {

			byte[] buffer = new byte[8 * 1024];
			String uri = null;
			int headLength = 0;// 请求头长度
			while (true) {
				int count = pushBackStream.read(buffer);
				for (int i = 0; i < count - 3; i++) {
					if (buffer[i + 0] == '\r' && buffer[i + 1] == '\n'
							&& buffer[i + 2] == '\r' && buffer[i + 3] == '\n') {
						headLength = i + 4;
						break;
					}
				}
				if (headLength <= 0) {
					// count == -1时意味着没有请求
					if (count == -1) {
						headLength = count;
					} else {
						pushBackStream.unread(buffer, 0, count);
					}
				}
				if (headLength > 0) {
					String head = new String(buffer, 0, headLength);
					uri = head.substring(head.indexOf(" ") + 1);
					uri = uri.substring(0, uri.indexOf(" "));
					uri = URLDecoder.decode(uri, "UTF-8");

					File file = new File(rootIndex, uri);
					// 如果是目录则继续遍历，是文件则发送
					if (!file.exists()) {
						sendNotFoundError(uri, output);
					} else if (file.isFile()) {
						sendFile(uri, output);
					} else {
						sendIndex(uri, output);
					}
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				clientSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 发送文件
	 * 
	 * @param uri
	 * @param out
	 * @throws IOException
	 */
	private void sendFile(String uri, OutputStream out) throws IOException {
		File file = new File(rootIndex, uri);
		long fileLength = file.length();

		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/1.1 200 OK\r\n");
		builder.append("Content-Type: " + "application/x-msdownload" + "\r\n");
		builder.append("Content-Length: " + fileLength + "\r\n");
		builder.append("\r\n");

		String sendStr = builder.toString();
		byte[] data = sendStr.getBytes();
		out.write(data);

		Path source = Paths.get(file.toURI());
		Files.copy(source, out);
	}

	/**
	 * 发送文件找不到错误
	 * 
	 * @param uri
	 * @param out
	 * @throws IOException
	 */
	private void sendNotFoundError(String uri, OutputStream out)
			throws IOException {
		// 响应体
		String content = "File[" + uri.substring(1) + "]Not Found";
		byte[] data = content.getBytes("UTF-8");
		// 响应头
		StringBuilder builder = new StringBuilder();
		builder.append("HTTP/1.1 404 Not Found\r\n");
		builder.append("Content-Type: text/html; charset=UTF-8\r\n");
		builder.append("Content-Length: " + data.length + "\r\n");
		builder.append("\r\n");

		String responseHead = builder.toString();
		out.write(responseHead.getBytes("UTF-8"));
		out.write(data);
	}

	/**
	 * 发送文件目录
	 * 
	 * @param name
	 * @param output
	 * @throws IOException
	 */
	public void sendIndex(String uri, OutputStream output) throws IOException {
		StringBuilder builder = new StringBuilder();
		File file = new File(rootIndex, uri);
		File[] names = file.listFiles();
		byte[] links = null;
		if (names == null)
			return;
		
		builder.append("HTTP/1.1 200 OK\r\n");
		builder.append("Content-Type: " + "text/html; charset=UTF-8" + "\r\n");
		builder.append("\r\n");

		builder.append("<div>" + "<a href='..'>" + "上级目录 " + "</a>" + "</div>");
		for (File temp : names) {
			// 当前显示目录
			String tmpPath = temp.getAbsolutePath();
			tmpPath = tmpPath.replace(rootIndex, "");
			tmpPath = tmpPath.replace("\\", "/");
			tmpPath = URLEncoder.encode(tmpPath, "UTF-8");
			// /表示目录路径 %2F
			// http://127.0.0.1:8001//book = http://127.0.0.1:8001/%2Fbook
			tmpPath = tmpPath.replace("%2F", "/");
			builder.append("<div>" + "<a href = '" + tmpPath + "'>"
					+ temp.getName() + "</a></div>");
		}
		links = builder.toString().getBytes("UTF-8");
		output.write(links);
	}

	public static void main(String[] args) {
		LocalServer server = new LocalServer();
		server.start();
	}
}
