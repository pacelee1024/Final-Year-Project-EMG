package gui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import control.Controller;
import shimmerSpecific.Shimmer;

@SuppressWarnings("serial")
public class GUI extends JFrame {
		
	private JList<Shimmer> devices;
	private JTextField port;
	private JButton connect;
	private JLabel connected, streaming;
	private JButton disconnectAll;
	private JButton stream;
	private JButton stopStream;
	
	public GUI(){
		
	Controller control = new Controller();
	
	// Set JPanel parameters
    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // Exit program if close-window button clicked
    this.setTitle("EMG Controller"); // "this" JFrame sets title
    this.setSize(450, 200);         // "this" JFrame sets initial size
    this.setVisible(true);          // "this" JFrame shows
    this.setResizable(false);
    
	// Get content pane and set it up.
	Container cp = getContentPane();
	cp.setLayout(new GridBagLayout());
	
	this.devices = new JList<Shimmer>(control.devices);
	this.devices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	this.devices.setLayoutOrientation(JList.VERTICAL);
	this.devices.setSelectedIndex(0);
	this.devices.addListSelectionListener(new ListSelectionListener(){
		@Override
		public void valueChanged(ListSelectionEvent arg0) {
			port.setText(devices.getSelectedValue().getPort());
			if(devices.getSelectedValue().isConnected()){
				connected.setText("Connected");
				connect.setEnabled(false);
			}
			else {
				connected.setText("Disconnected");
				connect.setEnabled(true);
			}
		}	
	});

    
    this.port = new JTextField(this.devices.getSelectedValue().getPort());
    this.port.setHorizontalAlignment(SwingConstants.CENTER);
    this.port.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			devices.getSelectedValue().setPort(port.getText());
		}
    });
    
    this.connect = new JButton("Connect to Device");
    this.connect.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			devices.getSelectedValue().connect();
		}
    });
    
    this.disconnectAll = new JButton("Disconnect all Devices");
    this.disconnectAll.setEnabled(true);
    this.disconnectAll.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			for(int i = 0; i < devices.getModel().getSize(); i++){
				Shimmer s = devices.getModel().getElementAt(i);
				if(s.isConnected() && !s.isStreaming()) s.disconnect();
			}
		}
    });
    
    this.stopStream = new JButton("Stop Streaming");
    this.stopStream.setEnabled(false);
    this.stopStream.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			disconnectAll.setEnabled(true);
			stopStream.setEnabled(false);
			stream.setEnabled(true);
			streaming.setText("Not Streaming");
			for(int i = 0; i < devices.getModel().getSize(); i++){
				Shimmer s = devices.getModel().getElementAt(i);
				if(s.isConnected() && s.isStreaming()) s.stopStreaming();
			}
		}
    });
    
    this.stream = new JButton("Start Streaming");
    this.stream.setEnabled(true);
    this.stream.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			disconnectAll.setEnabled(false);
			stream.setEnabled(false);
			stopStream.setEnabled(true);
			streaming.setText("Streaming");
			for(int i = 0; i < devices.getModel().getSize(); i++){
				Shimmer s = devices.getModel().getElementAt(i);
				if(s.isConnected() && !s.isStreaming()) s.startStreaming();
			}
		}
    });
    
    this.connected = new JLabel("Disconnected");
    this.connected.setHorizontalAlignment(SwingConstants.CENTER);
    
    this.streaming = new JLabel("Not Streaming");
    this.streaming.setHorizontalAlignment(SwingConstants.CENTER);
    
    GridBagConstraints c = new GridBagConstraints();
	
	c.gridheight = 3;
	c.gridwidth = 1;
	c.gridx = 1;
	c.gridy = 0;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.devices, c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 2;
	c.gridy = 0;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.connect, c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 2;
	c.gridy = 1;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.port, c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 2;
	c.gridy = 2;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.connected, c);
	
	c.gridheight = 1;
	c.gridwidth = 3;
	c.gridx = 0;
	c.gridy = 3;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.streaming,c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 0;
	c.gridy = 4;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.stream,c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 1;
	c.gridy = 4;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.stopStream,c);
	
	c.gridheight = 1;
	c.gridwidth = 1;
	c.gridx = 2;
	c.gridy = 4;
	c.fill = GridBagConstraints.BOTH;
	cp.add(this.disconnectAll,c);
	}
	
	public static void main(String[] args) {

	      SwingUtilities.invokeLater(new Runnable() {
	          @Override
	          public void run() {
	             new GUI();
	          }
	      });
	}
	
	public void close(){
		this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	}
	
}
