import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;


public class PingClient {
	private static final int TIME_OUT = 1000; // milliseconds

	public static void main(String args[]) throws Exception {
		if (args.length < 2) {
			System.out.println("Required arguements: hostName/hostAddress, portNumber");
			return;
		}

		// The port number to listen to.
		int port = Integer.parseInt(args[1]);

		// The server to connect to.
		InetAddress server;
		//pass in localhost or other address
		server = InetAddress.getByName(args[0]);

		// Create a datagram socket for sending and receiving UDP
		// packets through the port specified on the command line.
		DatagramSocket socket = new DatagramSocket();

		int sequence_number;

		long minRtt = 0;
		long maxRtt = 0;
		long averageDelay = 0;

		for (sequence_number = 0; sequence_number < 10; sequence_number++) {
			//timestamp
			Date currentTime = new Date();
			long msSend = currentTime.getTime();
			
			String str = "PING " + sequence_number + " " + msSend + "\r\n";

			byte[] buffer = str.getBytes();

			// Create a Ping datagram to the specified server
			DatagramPacket ping = new DatagramPacket(buffer, buffer.length, server, port);

			// Send the Ping data packet to the specified server
			socket.send(ping);

			// Try to receive the packet
			// Fail when timeout
			try {
				// Set the timeout to 1000ms = 1 second specified
				socket.setSoTimeout(TIME_OUT);

				// Create a datagram packet to hold incoming UDP packet.
				DatagramPacket response = new DatagramPacket(new byte[1024], 1024);

				// Try to receive the response from the server
				socket.receive(response);

				// Timestamp for the receive time
				currentTime = new Date();
				long msReceived = currentTime.getTime();

		 		long delay = msReceived - msSend;


				if (sequence_number == 0) {
					minRtt = delay;
					maxRtt = delay;
				}

		        // Calculate minimum delay and maximum delay.
		        if (delay < minRtt)
		        	minRtt = delay;
		        if (delay > maxRtt)
		        	maxRtt = delay;

		        // Calculate average delay.
		        averageDelay += delay / (sequence_number + 1);


		        // Print the packet and the delay
					printData(response, sequence_number, delay);
			} catch (IOException e) {
				//This is the case where the packet is lost
				// Print to indicates which packet is lost
				System.out.println("Timeout for packet " + sequence_number);
			}
		}
		System.out.println("min rtt = " + minRtt + " ms" +
						", max rtt = " + maxRtt + " ms" +
						", average rtt = " + averageDelay + " ms");
		socket.close();
	}

	/*
	 * Print ping data to the standard output stream.
	 * slightly changed from PingServer
	 */
	private static void printData(DatagramPacket request, int sequence, long delayTime) throws Exception{
		// Obtain references to the packet's array of bytes.
		byte[] buf = request.getData();

		// Wrap the bytes in a byte array input stream,
		// so that you can read the data as a stream of bytes.
		ByteArrayInputStream bais = new ByteArrayInputStream(buf);

		// Wrap the byte array output stream in an input stream reader,
		// so you can read the data as a stream of characters.
		InputStreamReader isr = new InputStreamReader(bais);

		// Wrap the input stream reader in a buffered reader,
		// so you can read the character data a line at a time.
		// (A line is a sequence of chars terminated by any combination of \r and \n.)
		BufferedReader br = new BufferedReader(isr);

		// The message data is contained in a single line, so read this line.
		String line = br.readLine();

		// Print host address and data received from it.
		System.out.println(
		"ping to " +
		request.getAddress().getHostAddress() + ": " +
		new String(line) + "\n" +
		"seq = " + sequence +
		", rtt = " + delayTime + " ms");
	}
}
