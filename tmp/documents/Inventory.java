import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
class Inventory {
 public static void main(String[] args)throws Exception{
 var jc=ToolProvider.getSystemJavaCompiler();
 var root=Path.of("app/src/main/java");
 var files=Files.walk(root).filter(p->p.toString().endsWith(".java")).sorted().toList();
 try(var fm=jc.getStandardFileManager(null,null,java.nio.charset.StandardCharsets.UTF_8)){
 var task=(JavacTask)jc.getTask(null,fm,null,List.of("-proc:none"),null,fm.getJavaFileObjectsFromPaths(files));
 var rows=new ArrayList<String>();
 for(var cu: task.parse()) for(var td:cu.getTypeDecls()) if(td instanceof ClassTree c) walk(c,"",rows);
 Files.write(Path.of("tmp/documents/members.tsv"),rows);
 }}
 static void walk(ClassTree c,String pre,List<String> rows){
 var n=pre+c.getSimpleName();
 for(var m:c.getMembers()){
 if(m instanceof VariableTree v) rows.add(String.join("\t",n,"F",v.getName().toString(),v.getType()==null?"":v.getType().toString(),v.getModifiers().getFlags().toString()));
 if(m instanceof MethodTree v && !v.getName().toString().startsWith("<")) rows.add(String.join("\t",n,"M",v.getName().toString(),v.getReturnType().toString(),v.getModifiers().getFlags().toString()));
 if(m instanceof ClassTree t) walk(t,n+".",rows);
 }} }
