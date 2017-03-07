/**
 * Created by King on 3/2/2017.
 */
import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;

import static java.lang.System.exit;


class UDPClient {
    public static DatagramSocket clientSocket;


    public static void main(String args[]) throws Exception {

        clientSocket = new DatagramSocket();
        UDPClientHelper client = new UDPClientHelper("127.0.0.1");

        BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(System.in));
        try {
            String httpRequest = inputBuffer.readLine();
            client.sendHTTPRequest(httpRequest);
            client.receivePacket();
        } catch (IOException e) {
            System.out.println("Client error " + e.getLocalizedMessage());
            throw e;
        }

    }
}

class UDPClientHelper {
    private byte[] sendData = new byte[1024];
    private byte[] receiveData = new byte[1024];
    private InetAddress ipAddress;

    public UDPClientHelper(String ipAddressAssigned) throws UnknownHostException {
        try {
            ipAddress = InetAddress.getByName(ipAddressAssigned);
        } catch (UnknownHostException e) {
            System.out.println("Unable to resolve host " + e.getLocalizedMessage());
            throw e;
        }
    }

    public void sendHTTPRequest(String request) throws IOException {
        sendData = request.getBytes();

        // Create the datagram packet
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, 10040);
        try {
            UDPClient.clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Error sending packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    public void receivePacket() throws IOException {
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try {
            UDPClient.clientSocket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("Error receiving packet... " + e.getLocalizedMessage());
            throw e;
        }

        String response = new String(receivePacket.getData());
        System.out.println("Received message from server... " + response);
        UDPClient.clientSocket.close();
    }
}
