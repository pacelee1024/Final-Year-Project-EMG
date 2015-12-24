package data;

public class RMS {

	private double[] data;
	
	private int noSamples = 200;
	private int index = 0;
	
	public RMS(int noSamples){
		this.noSamples = noSamples;
		 data = new double[this.noSamples];
	}
	
	/*
	 * To save calculation time we square new data as we insert it
	 * 
	 * Return null if window is not full
	 */
	public Double doRMS(double latestData){
		// If not enough samples are present
		if(this.index < this.noSamples - 1){
			this.data[index] = (latestData * latestData);
			this.index++;
			return null;
		}
		// If this sample completes the window
		else if(this.index == this.noSamples - 1){
			this.data[index] = (latestData * latestData);
			this.index++;
			return this.doCalc();
		}
		// If window is full and we need to move
		else{
			for(int i = 0; i < (this.noSamples - 1);i++){
				this.data[i] = this.data[i + 1];
				}
			this.data[this.noSamples - 1] = (latestData * latestData);
			return doCalc();
		}			
	}
	
	private double doCalc(){
		double runningTotal = 0;
		
		for(double d: this.data){	
			runningTotal += d;
		}
		
		runningTotal /= this.noSamples;
		return Math.sqrt(runningTotal);
	}
}
