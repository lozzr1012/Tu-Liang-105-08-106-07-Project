

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class OneFTT {

  public static class TokenizerMapper 
       extends Mapper<Object, Text, Text, IntWritable>{
    
    private final static IntWritable one = new IntWritable(1);
    private Text Integer = new Text();
    protected void setup(Context context)
    {
    	System.out.println("Mapper Setup: " + ((FileSplit)context.getInputSplit()).getPath().getName());
    }
    protected void cleanup(Context context)
    {
    	System.out.println("Mapper Cleanup");
    }
    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      StringTokenizer itr = new StringTokenizer(value.toString());

      while (itr.hasMoreTokens()) {
      //    String[] temp=value.toString().split(" ");
        //  for(int i=0;i<temp.length;i++)
         // {
        //	  Integer.set(temp[i]);
    	  Integer.set(itr.nextToken());
        	  context.write(Integer, one);
        //  }
        
      }
      
    }
  }
  
  public static class IntSumReducer 
       extends Reducer<Text,IntWritable,Text,IntWritable> {
    private IntWritable result = new IntWritable();
    protected void setup(Context context)
    {
    	System.out.println("Reducer Setup");
    }
    protected void cleanup(Context context)
    {
    	System.out.println("Reducer Cleanup");
    	
    }
    public void reduce(Text key, Iterable<IntWritable> values, 
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }

	public static void main(String[] args) throws Exception {

		boolean useJobTracker = true;
		
		//Step 0. Set configuration
		Configuration conf = new Configuration();
		/*
		conf.set("fs.default.name", "hdfs://localhost:9000");
		if(useJobTracker)
			conf.set("mapred.job.tracker", "localhost:9001");
		else
			conf.set("mapred.job.tracker", "local");
		*/
		
		FileSystem hdfs = FileSystem.get(conf);
		System.out.println("Working Directory -> " + hdfs.getWorkingDirectory().toString());
		
		//Path inputPath = new Path(hdfs.getWorkingDirectory().toString() + "/dataset");
		//Path outputPath = new Path(hdfs.getWorkingDirectory().toString() + "/final");
		
		//Step 1. prepare data		
		//Step 2. Create a Job
		Job job = new Job(conf,"President Election");
		job.setJarByClass(OneFTT.class);
		
		//Step 3. Set Input format
		job.setInputFormatClass(TextInputFormat.class);
		
		//Step 4. Set Mapper
		job.setMapperClass(TokenizerMapper.class);
		
		//Step 4.1 Set Combiner (Local Reducer)
		job.setCombinerClass(IntSumReducer.class);
		
		//Step 5. Set Reducer
		job.setReducerClass(IntSumReducer.class);
		
		
		//Step 7. Set Output
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		
		FileInputFormat.addInputPath(job, new Path(args[0]));
	    FileOutputFormat.setOutputPath(job, new Path(args[1]));	

		//Step 8. Submit Job
		job.submit();
		
		if(job.waitForCompletion(true))
		{
			System.out.println("Job Done!");
			System.exit(0);
		}
		else
		{
			System.out.println("Job Failed!");
			System.exit(1);
		}    
	}
}

