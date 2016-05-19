package csefsck;

import java.io.*;
import java.util.*;

public class csefsck {
	static int creationTime,mounted,devId,freeStart,freeEnd,root,maxBlocks;
	static List<Integer> freeBlockList1;
	static boolean[] nofreeblock;
	public static void readSuper() {
		try {
			FileReader reader = new FileReader("fusedata.0"); // 读取文本中内容
			BufferedReader br = new BufferedReader(reader);
			String s="";
			while(true){
				String str = br.readLine();
				if(str==null) break;
				s=s+str;
			}
			String q[]= s.split(", |: |:|,|}");
			creationTime=Integer.parseInt(q[1]);
			mounted=Integer.parseInt(q[3]);
			devId=Integer.parseInt(q[5]);
			freeStart=Integer.parseInt(q[7]);
			freeEnd=Integer.parseInt(q[9]);
			root=Integer.parseInt(q[11]);
			maxBlocks=Integer.parseInt(q[13]);
			nofreeblock=new boolean[maxBlocks];
			long unixTime = System.currentTimeMillis() / 1000L;
			if (creationTime>unixTime)
				System.out.println( "Creation time is NOT correct");
		    }catch (IOException e) {
			e.printStackTrace();
			}
    }
	public static void checkDevId()
	{
		if (devId!=20) System.out.println("Device ID is NOT correct"); 
	}
	public static void readFreeBlockList()
	{
		freeBlockList1=new LinkedList<Integer>();
		for (int i=freeStart;i<=freeEnd;i++)
		{
			String t="fusedata."+i;
			try {
				FileInputStream inputStream = new FileInputStream(t);
				Scanner sc = new Scanner(inputStream);
				sc.useDelimiter(", |,| "); 
				while(sc.hasNextInt())
					freeBlockList1.add(sc.nextInt());
				
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			
		}
	}
	public static void checkBlocks(int i) 
	{
			String t="fusedata."+i;
			File f=new File(t);
			if (f.exists())
			{
					Map map=ReadBlock(i);
					if(map.containsKey("size"))
					{	
						nofreeblock[i]=true;
						/*All times are in the past, nothing in the future*/
						long unixTime = System.currentTimeMillis() / 1000L;
						if ((int)map.get("atime")>unixTime||(int)map.get("ctime")>unixTime||(int)map.get("mtime")>unixTime)
							System.out.println( "Block "+i+" time is NOT correct");
						
						if (map.containsKey("filename_to_inode_dict"))
						{
							/*checkEach directory contains . and .. and their block numbers are correct*/
							Map Dmap=(Map) map.get("filename_to_inode_dict");
							if(!Dmap.containsKey("d.")|!Dmap.containsKey("d..")) System.out.println("Block "+i+" does NOT contain . or ..");
							else
							{
								
								if ((int)Dmap.get("d.")!=i)
									System.out.println( "Block "+i+" d. number is NOT correct");
								int fNum=(int)Dmap.get("d..");
								Map fMap=ReadBlock(fNum);
								Map fDmap=(Map) fMap.get("filename_to_inode_dict");
								if (!fMap.containsKey("filename_to_inode_dict")||!fDmap.containsValue(i))
									System.out.println( "Block "+i+" d.. number is NOT correct");
								Iterator<Map.Entry> entries = Dmap.entrySet().iterator();
								while (entries.hasNext())
								{
									Map.Entry<String, Integer> entry = entries.next();
									if (!entry.getKey().equals("d.")&&!entry.getKey().equals("d.."))
										checkBlocks(entry.getValue());

								}
								
							}
							/*Each directory’s link count matches the number of links in the filename_to_inode_dict*/
							if ((int)map.get("linkcount")!=Dmap.size())
								System.out.println( "Block "+i+" linkout number is NOT correct");
							
						}else
						{
							nofreeblock[(int) map.get("location")]=true;			
								int block=(int)map.get("location");
								try {
									FileReader reader = new FileReader("fusedata."+block); 
									BufferedReader br = new BufferedReader(reader);
									String s="";
									while(true){
										String str = br.readLine();
										if(str==null) break;
										s=s+str;
									}
									//System.out.println(s);	
									String q[]= s.split(", |,| ");
									
									if ((int)map.get("indirect")==1)
									{
										/*If the data contained in a location pointer is an array, that indirect is one*/
										if (q.length<=1)
											System.out.println( "Block "+i+" indirect is NOT correct"); 
											for (int j=0;j<q.length;j++)
											{
												if (!isNumeric(q[j]))
												{	
													System.out.println( "Block "+i+" indirect is NOT correct");
													break;
												}												
												else nofreeblock[Integer.parseInt(q[j])]=true;
											}
										/*size<blocksize*length of location array if indirect!=0
												b.	size>blocksize*(length of location array-1) if indirect !=0*/
                                        if((int)map.get("size")>4096*q.length||(int)map.get("size")<4096*(q.length-1))
                                        	System.out.println( "Block "+i+" has INVALID size");							
									}else
									{
										/*size<blocksize if  indirect=0 and size>0*/
                                       if ((int)map.get("size")>4096||(int)map.get("size")<0)
                                    	   System.out.println( "Block "+i+" has INVALID size");
									}
								}catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}
		}
	public static Map ReadBlock(int i)
	{
		Map map=new HashMap();
		try {
			FileReader reader = new FileReader("fusedata."+i); 
			BufferedReader br = new BufferedReader(reader);
			String s="";
			while(true){
				String str = br.readLine();
				if(str==null) break;
				s=s+str;
			}
			String q[]= s.split(", |: |:|,|}| ");
			String t="";
			for (int j=0;j<q.length;j++)
			{
				if (!isNumeric(q[j]))
				{
					if (q[j].charAt(0)=='{')
						q[j]=q[j].substring(1);
					if (q[j].equals("filename_to_inode_dict"))
					{
						
						Map Dmap=new HashMap();
						j++;
						for(;j<q.length;j++)
						{
							if (!isNumeric(q[j]))
							{	
								if (q[j].charAt(0)=='{')
							    q[j]=q[j].substring(1);
								t=t+q[j];
						    }else
						    {
						    	Dmap.put(t, Integer.parseInt(q[j]));
								t="";
						    }
							
						}
						map.put("filename_to_inode_dict",Dmap);
					
					}
					else 
						t=t+q[j];
				}else
				{	
					map.put(t, Integer.parseInt(q[j]));
					t="";
				}
			}
		    }catch (IOException e) {
			e.printStackTrace();
			}
		return map;
	}
	public static boolean isNumeric(String str)
	{
		for (int i = 0; i < str.length(); i++)
		{
		   if (!Character.isDigit(str.charAt(i)))
			   return false;
		 }
		 return true;
	}
	public static void checkFree()
	{
		for (int i=freeEnd+1;i<maxBlocks;i++)
		{
			if (nofreeblock[i]==true&&freeBlockList1.contains(i))
			{
				System.out.println(i+" is NOT free block list but on the list");

			}		
			if (nofreeblock[i]==false&&!freeBlockList1.contains(i))	
			{
				System.out.println(i+" is free block list but NOT on the list");
			}
		}
	}
	public static void csefsck()
	{
		readSuper();
		checkDevId();
		readFreeBlockList();
		checkBlocks(root);
		checkFree();
	}
	public static void main(String[] args) {
		csefsck();
	}
}
