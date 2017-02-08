import fptree.TreeNode;

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
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.LineReader;


public class abc123 {

    private static final int minSuport = 30; // 最小支持度

    public static class GroupMapper extends
            Mapper<LongWritable, Text, Text, Text> {

        LinkedHashMap<String, Integer> freq = new LinkedHashMap<String, Integer>(); // 频繁1项集



        /**
         * 读取频繁1项集
         */
        @Override
        public void setup(Context context) throws IOException {
            // 初始化IK分词器

            // 从HDFS文件读入频繁1项集，即读取IMWordCount的输出文件，要求已经按词频降序排好
            Configuration conf = context.getConfiguration();
            FileSystem fs = FileSystem.get(conf);
            Calendar cad = Calendar.getInstance();
            cad.add(Calendar.DAY_OF_MONTH, -1); // 昨天
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String yes_day = sdf.format(cad.getTime());
            Path freqFile = new Path("/dsap/resultdata/content/WordCount/"
                    + yes_day + "/part-r-00000");

            FSDataInputStream fileIn = fs.open(freqFile);
            LineReader in = new LineReader(fileIn, conf);
            Text line = new Text();
            while (in.readLine(line) > 0) {
                String[] arr = line.toString().split("\\s+");
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
              //   if (arr.length == 4) {
                     List<String> content = new ArrayList<String>();
                     for(int i=0;i<arr.length;i++)
                     	content.add(arr[i]);
                     	
                     
                     List<String> result = content;
                     List<String> list = new LinkedList<String>();
                     for (String ele : result) {
                        
                         if (freq.containsKey(ele)) {
                          //   list.add(ele.toLowerCase()); // 憒���望�摮�嚗�蝏�頧祆銝箏���
                         	list.add(ele);
                         }
                     }

                // 对事务项中的每一项按频繁1项集排序
                Collections.sort(list, new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        return freq.get(s2) - freq.get(s1);
                    }
                });

                /**
                 * 比如对于事务(中国，人民，人民，广场)，输出(中国，人民)、(中国，人民，广场)
                 */
                List<String> newlist = new ArrayList<String>();
                newlist.add(list.get(0));
                for (int i = 1; i < list.size(); i++) {
                    // 去除list中的重复项
                    if (!list.get(i).equals(list.get(i - 1))) {
                        newlist.add(list.get(i));
                    }
                }
                for (int i = 1; i < newlist.size(); i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j <= i; j++) {
                        sb.append(newlist.get(j) + "\t");
                    }
                    context.write(new Text(newlist.get(i)),
                            new Text(sb.toString()));
                }
            }
        }
    }

    public static class FPReducer extends
            Reducer<Text, Text, Text, IntWritable> {
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            List<List<String>> trans = new LinkedList<List<String>>(); // 事务数据库
            while (values.iterator().hasNext()) {
                String[] arr = values.iterator().next().toString()
                        .split("\\s+");
                LinkedList<String> list = new LinkedList<String>();
                for (String ele : arr)
                    list.add(ele);
                trans.add(list);
            }
            List<TreeNode> leafNodes = new LinkedList<TreeNode>(); // 收集FPTree中的叶节点
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
                // Collections.sort(associateRrule); //从根节点到叶节点已经按F1排好序了，不需要再排序了
                StringBuilder sb = new StringBuilder();
                for (String ele : associateRrule) {
                    sb.append(ele + "|");
                }
                // 因为一句话可能包含重复的词，所以即使这些词都是从F1中取出来的，到最后其支持度也可能小于最小支持度
                if (frequency > minSuport) {
                    context.write(new Text(sb.substring(0, sb.length() - 1)
                            .toString()), new IntWritable(frequency));
                }
            }
        }

        // 构建FP-Tree
        public TreeNode buildFPTree(List<List<String>> records,
                List<TreeNode> leafNodes) {
            TreeNode root = new TreeNode(); // 创建树的根节点
            for (List<String> record : records) { // 遍历每一项事务
                // root.printChildrenName();
                insertTransToTree(root, record, leafNodes);
            }
            return root;
        }

        // 把record作为ancestor的后代插入树中
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
                        leafNodes.add(node); // 把叶节点都放在一个链表中
                    }
                    insertTransToTree(node, record, leafNodes);
                }
            }
        }
   

    public static void main(String[] args) throws IOException,
            InterruptedException, ClassNotFoundException {
        Configuration conf = new Configuration();
        String[] argv = new GenericOptionsParser(conf, args).getRemainingArgs();
        if (argv.length < 2) {
            System.err
                    .println("Usage: MR_FPTree EcryptedChartContent AssociateRules");
            System.exit(1);
        }

        FileSystem fs = FileSystem.get(conf);
        Path inpath = new Path(argv[0]);
        Path outpath = new Path(argv[1]);
        fs.delete(outpath, true);

        Job FPTreejob = new Job(conf, "MR_FPTree");
        FPTreejob.setJarByClass(abc123.class);

    
        FPTreejob.setInputFormatClass(TextInputFormat.class);
        FileOutputFormat.setOutputPath(FPTreejob, outpath);

        FPTreejob.setMapperClass(GroupMapper.class);
        FPTreejob.setMapOutputKeyClass(Text.class);
        FPTreejob.setMapOutputValueClass(Text.class);

        FPTreejob.setReducerClass(FPReducer.class);
        FPTreejob.setOutputKeyClass(Text.class);
        FPTreejob.setOutputKeyClass(IntWritable.class);

        FPTreejob.waitForCompletion(true);
    }
}