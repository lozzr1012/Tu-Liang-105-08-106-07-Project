import fptree.TreeNode;
import java.io.BufferedReader;  
import java.io.BufferedWriter;  
import java.io.FileReader;  
import java.io.FileWriter; 
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.LineReader;

public class aaa123 {

	private static final int minSuport =0 ; // 最小支持度

	public static class GroupMapper extends
			Mapper<LongWritable, Text, Text, Text> {

		LinkedHashMap<String, Integer> freq = new LinkedHashMap<String, Integer>(); // 頻繁1項目集

		/*
		 * 讀取頻繁1項目集
		 */
		@Override
		public void setup(Context context) throws IOException {

			Configuration conf = context.getConfiguration();
			FileSystem fs = FileSystem.get(conf);
			Calendar cad = Calendar.getInstance();
			cad.add(Calendar.DAY_OF_MONTH, -1); // �典予
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			String yes_day = sdf.format(cad.getTime());
			Path freqFile = new Path(fs.getWorkingDirectory().toString()
					+ "/final/part-r-00000");

			FSDataInputStream fileIn = fs.open(freqFile);
			LineReader in = new LineReader(fileIn, conf);
			Text line = new Text();
			while (in.readLine(line) > 0) {

				String[] arr = line.toString().split("\\s+");
				/*
				 * String[] arr = line.toString().split("	");
				 * System.out.println(arr[0]);
				 */
				if (arr.length == 2) {
					int count = Integer.parseInt(arr[1]);
					// 只读取词频大于最小支持度的

					if (count > minSuport) {
						String word = arr[0];
						freq.put(word, count);
					}
				}
			}
			in.close();

		}

		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String[] arr = value.toString().split("");
			// if (arr.length == 4) {
			List<String> content = new ArrayList<String>();
			for (int i = 0; i < arr.length; i++)
			{
			
				content.add(arr[i]);
			}
			List<String> result = content;
			List<String> list = new LinkedList<String>();
						
			for (String ele : result) {

				if (freq.containsKey(ele)) {
					//list.add(ele.toLowerCase()); //
					// 憒���望�摮�嚗�蝏�頧祆銝箏���
					list.add(ele);
				}
			}
			
			
			Collections.sort(list, new Comparator<String>() {
				@Override
				public int compare(String s1, String s2) {
					return freq.get(s2) - freq.get(s1);
				}
			});

			/**
			 * 瘥�撖嫣�鈭(銝剖嚗犖瘞�鈭箸�嚗嘀��嚗���銝剖嚗犖瘞���銝剖嚗犖瘞�撟踹)
			 */
			List<String> newlist = new ArrayList<String>();
			newlist.add(list.get(0));
			for (int i = 1; i < list.size(); i++) {
				if (!list.get(i).equals(list.get(i - 1))) {
					newlist.add(list.get(i));
				}
			}
			for (int i = 1; i < newlist.size(); i++) {
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j <= i; j++) {
					sb.append(newlist.get(j) + "\t");
				}
				context.write(new Text(newlist.get(i)), new Text(sb.toString()));
			}
		}

	}

	public static class FPReducer extends
			Reducer<Text, Text, Text, IntWritable> {
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			List<List<String>> trans = new LinkedList<List<String>>(); // 鈭�唳摨�
			while (values.iterator().hasNext()) {
				String[] arr = values.iterator().next().toString()
						.split("\\s+");
				LinkedList<String> list = new LinkedList<String>();
				for (String ele : arr)
					list.add(ele);
				trans.add(list);
			}
			List<TreeNode> leafNodes = new LinkedList<TreeNode>(); // 收集FPtree中節點
			buildFPTree(trans, leafNodes);
			for (TreeNode leaf : leafNodes) {
				TreeNode tmpNode = leaf;
				List<String> associateRrule = new ArrayList<String>();
				int frequency = 0;
				while (tmpNode.getParent() != null) {
					associateRrule.add(tmpNode.getName());
					frequency = tmpNode.getCount();
					tmpNode = tmpNode.getParent();
				}
				// Collections.sort(associateRrule);
				StringBuilder sb = new StringBuilder();
				for (String ele : associateRrule) {
					sb.append(ele + " ");
				}
				// �蛹銝�霂�賢��恍�憭�霂���誑�喃蝙餈�霂�臭�F1銝剖��箸���唳���舀�摨虫��航撠�����舀�摨�
				if (frequency > minSuport) {
					context.write(new Text(sb.substring(0, sb.length() - 1).toString()), new IntWritable(frequency));
				}
			}
		}

		// �遣FP-Tree
		public TreeNode buildFPTree(List<List<String>> records,
				List<TreeNode> leafNodes) {
			TreeNode root = new TreeNode(); // �遣���寡���
			for (List<String> record : records) { // ��瘥�憿嫣���
				// root.printChildrenName();
				insertTransToTree(root, record, leafNodes);
			}
			return root;
		}

		// �ecord雿蛹ancestor��隞���交�銝�
		public void insertTransToTree(TreeNode root, List<String> record,
				List<TreeNode> leafNodes) {
			if (record.size() > 0) {
				String ele = record.get(0);
				record.remove(0);
				if (root.findChild(ele) != null) {
					root.countIncrement(1);
					root = root.findChild(ele);
					insertTransToTree(root, record, leafNodes);
				} else {
					TreeNode node = new TreeNode(ele);
					root.addChild(node);
					node.setCount(1);
					node.setParent(root);
					if (record.size() == 0) {
						leafNodes.add(node); // ���賣�其�銝芷銵其葉
					}
					insertTransToTree(node, record, leafNodes);
				}
			}
		}
	}

	public static void main(String[] args) throws IOException,
			InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();

		FileSystem fs = FileSystem.get(conf);
	    FileWriter fw = new FileWriter("C:\\Users/Eason/Desktop/infrequent1.txt");
	    
		System.out.println(fs.getWorkingDirectory().toString() + "/dataset");
		
		Path inpath = new Path(fs.getWorkingDirectory().toString() + "/dataset");
		
		 BufferedWriter bufw = new BufferedWriter(fw);  
		
		 bufw.write(fs.getWorkingDirectory().toString());
		 
		/*Path outpath = new Path(fs.getWorkingDirectory().toString()
				+ "/frequency");
		*/
		// fs.delete(outpath, true);
		 bufw.close();  
		

		Job FPTreejob = new Job(conf, "test132");
		FPTreejob.setJarByClass(test132.class);

		FPTreejob.setInputFormatClass(TextInputFormat.class);
		//FileOutputFormat.setOutputPath(FPTreejob, outpath);

		// Step 6. Set Input
		FileInputFormat.addInputPath(FPTreejob, inpath);
/*
		// Step 7. Set Output
		FPTreejob.setOutputKeyClass(Text.class);
		FPTreejob.setOutputValueClass(IntWritable.class);
		if (fs.exists(outpath))
			fs.delete(outpath, true);
		FileOutputFormat.setOutputPath(FPTreejob, outpath);

		FPTreejob.setMapperClass(GroupMapper.class);
		FPTreejob.setMapOutputKeyClass(Text.class);
		FPTreejob.setMapOutputValueClass(Text.class);

		FPTreejob.setReducerClass(FPReducer.class);
		FPTreejob.setOutputKeyClass(Text.class);
		FPTreejob.setOutputKeyClass(IntWritable.class);
*/
		FPTreejob.waitForCompletion(true);
	}
}