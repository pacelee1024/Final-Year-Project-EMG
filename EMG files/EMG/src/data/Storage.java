package data;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import control.Controller;


public class Storage implements Runnable {
		
	// File logging variables
	private boolean fileLogEN = false;
	private String fileName = "log.cvs";
	private File file;
	private BufferedWriter logger;
	
	// So we can access shared data and store it in this class
	private Controller control;
	private Data[] latest = new Data[Config.SAMPLES];
	
	// For thread pool
	ExecutorService executor;
	
	
	
	public Storage(boolean toFile, String fileName, Controller c){
		
		this.fileLogEN = toFile;
		this.fileName = fileName;
		this.control = c;
		
		executor= Executors.newFixedThreadPool(20);
				 
		// Set everything up for logging to file
		
		if(this.fileLogEN){
			
			try {
				this.file = new File(this.fileName);
				if(!this.file.exists())this.file.createNewFile();
				this.logger = new BufferedWriter(new FileWriter(this.file.getAbsoluteFile()));		
			
				this.logger.write("TimeStamp");
				
				for(int i = 0; i < Config.TAGS.length; i++){
					this.logger.write(Config.DELIM);
					this.logger.write(Config.TAGS[i]);
				}
				this.logger.newLine();
			} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}			
		}		
	}
	
	private void logFile(){
		
	
		for(int i = 0; i < Config.SAMPLES; i++){
			String line = "";
			line = line + this.latest[i].getTimeStamp();
			
			for(int j = 0; j < Config.TAGS.length;j++){
				line += Config.DELIM;
				if(this.latest[i].getTags().contains(Config.TAGS[j])){
					line += this.latest[i].getData(Config.TAGS[j]);
				}
				else{
					line += " ";
				}
			}
			try {
				this.logger.write(line);
				this.logger.newLine();
			} catch (IOException e) {
				
				e.printStackTrace();
			}
		}
	}
	
	private void LogServer(){
		
		Long[] timeStamp = new Long[Config.SAMPLES];
		
		for(int i = 0; i < Config.SAMPLES; i++){
			timeStamp[i] = this.latest[i].getTimeStamp();
		}
		
		double[] values = new double[Config.TAGS.length];
		
		String str = "[";
				
		for(int i = 0; i < Config.SAMPLES; i++){
			
			if(i == 0) str += "{\"timestamp\":"+ timeStamp[i];
			else str += ",{\"timestamp\":"+ timeStamp[i];
			
			for(int j = 1; j < Config.TAGS.length;j+=2){
				if(this.latest[i].getTags().contains(Config.TAGS[j])){
					values[j] = this.latest[i].getData(Config.TAGS[j]);
				}
				else{
					values[j] = 0;
				}
			}
			str += ",\"emg1\":" + values[1] + ",\"emg2\":" + values[3] + ",\"emg3\":" + values[5] 
					+ ",\"emg4\":" + values[7] + ",\"emg5\":" + values[9] + ",\"emg6\":" + values[11] + "}";
		}
		str += "]";	
		
		executor.execute(new Request(str));
	}
	
	class Request implements Runnable{

		private String address = "";
		private URL url;
		public Request(String addr){
			this.address = addr;
		}
		public void run() {			
			
			 HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead 

			    try {
			        HttpPost request = new HttpPost(Config.ADDR);
			       
			        request.setHeader("Content-type", "application/json");
			        StringEntity params =new StringEntity(this.address);
			        request.setEntity(params);
			        HttpResponse response = httpClient.execute(request);

			        System.out.println(response.toString());
			        // handle response here...
			    }catch (Exception ex) {
			        // handle exception here
			    }
			    
			System.out.println(address);
		}
		
	}

	@Override
	/*
	 * This will continuously run, checking the archive in the controller for
	 * data needing to be archived. It will wait a certain time before logging
	 * in order to ensure all data is present.
	 */
	public void run() {

		// Current time for comparison
		long tc = 0;
		// Temporary storage to allow checking of time requirements
		Data temp[];

		// Infinite loop
		while(true){
			
			temp = new Data[Config.SAMPLES];
			for(int i = 0; i < Config.SAMPLES; i++){
				temp[i] = null;
			}
			
			// latest will be null when nothing has been logged yet
			if(this.latest[Config.SAMPLES-1] == null){
				if(!this.control.archive.isEmpty()){
					if(this.control.archive.size() > Config.SAMPLES){
						temp[0] = this.control.archive.first();
						for(int i = 1; i < Config.SAMPLES; i++){
							temp[i] = this.control.archive.higher(temp[i - 1]);
						}
					}
					
				}
			}
			else{
				// If logging has already started, get next record
				Data next[] = new Data[Config.SAMPLES];
				
				int div = Config.SENDPERIOD / Config.SAMPLES;
				
				for(int i = 0; i < Config.SAMPLES; i++){
					
					int div2 = div * (i+1);
					next[i] = new Data(this.latest[Config.SAMPLES-1].getTimeStamp() + div2, "test", 0);
					temp[i] = this.control.archive.ceiling(next[i]);
				}
			}
			
			// temp will be null if there is no next record
			if(temp[Config.SAMPLES-1] != null){
				// Timestamp of record
				long t1 = temp[Config.SAMPLES-1].getTimeStamp();
				// Current time
				tc = System.currentTimeMillis();
				
				// Check that threshold time has passed since record creation
				if((tc - t1) > 500){
						this.latest = temp;
						this.control.archive.removeAll(this.control.archive.headSet(this.latest[Config.SAMPLES-1]));
						this.LogServer();				
						if(this.fileLogEN) this.logFile();
				}
			}
				// Allow for new data to be added
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
}


