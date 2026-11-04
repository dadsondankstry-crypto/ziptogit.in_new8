package com.exemplo.exploradorzip;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.text.InputType;
import java.text.SimpleDateFormat;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public class MainActivity extends Activity {
  static final int FOLDER=1, ZIP=2, EXTRACT=3, MOVE=4, PACK=5, COPY=6, SHARE=7;
  final ExecutorService worker=Executors.newSingleThreadExecutor();
  final ArrayList<Row> rows=new ArrayList<>();
  final ArrayList<String> pathNames=new ArrayList<>(), pathIds=new ArrayList<>();
  final ArrayList<ZItem> zipItems=new ArrayList<>();
  Uri root, zipFile; String docId,zipName="",prefix="";
  boolean inZip=false, hidden=false, busy=false;
  final LinkedHashSet<String> selected=new LinkedHashSet<>();
  TextView path,status; ListView list; Button extract, moveButton, packButton;
  android.content.SharedPreferences prefs;
  String sortMode="name", search=""; boolean reverse=false, includeContainer=false;
  final ArrayList<String> history=new ArrayList<>();
  final LinkedHashSet<String> favorites=new LinkedHashSet<>();
  boolean cancelTask=false; TextView progressText;
  Uri lastDestination;


  static class Row {
    String name,id,note; boolean folder,zip; long size,modified; String mime;
    Row(String n,String i,String s,boolean f,boolean z){name=n;id=i;note=s;folder=f;zip=z;}
    public String toString(){return (folder?"📁 ":zip?"🗜 ":"📄 ")+name+"\n"+note;}
  }
  static class ZItem {
    String name; boolean folder; long size;
    ZItem(String n,boolean f,long s){name=n;folder=f;size=s;}
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);
    prefs=getSharedPreferences("exploradorzip_v2",MODE_PRIVATE);
    hidden=prefs.getBoolean("hidden",true);
    sortMode=prefs.getString("sort","name");reverse=prefs.getBoolean("reverse",false);
    try{org.json.JSONArray f=new org.json.JSONArray(prefs.getString("favorites","[]"));for(int j=0;j<f.length();j++)favorites.add(f.getString(j));}catch(Exception ignored){}

    LinearLayout base=new LinearLayout(this);base.setOrientation(1);base.setPadding(14,12,14,12);base.setBackgroundColor(0xfff6f8fb);
    TextView heading=new TextView(this);heading.setText("Explorador ZIP  ·  Offline");heading.setTextSize(22);heading.setTextColor(0xff172b48);base.addView(heading);
    LinearLayout bar=new LinearLayout(this);bar.setOrientation(0);
    Button folder=button("📁 Pasta"),z=button("🗜 ZIP"),back=button("← Voltar");
    bar.addView(folder,new LinearLayout.LayoutParams(0,-2,1));bar.addView(z,new LinearLayout.LayoutParams(0,-2,1));bar.addView(back,new LinearLayout.LayoutParams(0,-2,1));base.addView(bar);
    folder.setOnClickListener(v->chooseFolder(FOLDER));z.setOnClickListener(v->chooseZip());back.setOnClickListener(v->goBack());
    CheckBox check=new CheckBox(this);check.setText("Mostrar ocultos (.github, .gitignore...)");
    check.setChecked(hidden);
    check.setOnCheckedChangeListener((v,on)->{hidden=on;prefs.edit().putBoolean("hidden",on).apply();refresh();});base.addView(check);
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(0);
    Button tools=button("☰ Arquivos"), archive=button("🗜 ZIP+"), places=button("★ Locais");
    nav.addView(tools,new LinearLayout.LayoutParams(0,-2,1));nav.addView(archive,new LinearLayout.LayoutParams(0,-2,1));nav.addView(places,new LinearLayout.LayoutParams(0,-2,1));base.addView(nav);
    tools.setOnClickListener(v->showOperations());archive.setOnClickListener(v->showArchiveTools());places.setOnClickListener(v->showLocations());
    LinearLayout filters=new LinearLayout(this);
    Button searchButton=button("🔎 Buscar"),sortButton=button("↕ Ordem"),selectButton=button("☑ Seleção");
    filters.addView(searchButton,new LinearLayout.LayoutParams(0,-2,1));filters.addView(sortButton,new LinearLayout.LayoutParams(0,-2,1));filters.addView(selectButton,new LinearLayout.LayoutParams(0,-2,1));base.addView(filters);
    searchButton.setOnClickListener(v->searchDialog());sortButton.setOnClickListener(v->sortDialog());selectButton.setOnClickListener(v->selectDialog());
    path=new TextView(this);path.setTextColor(0xff275786);path.setPadding(2,8,2,8);base.addView(path);
    list=new ListView(this);base.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    list.setOnItemClickListener((a,v,pos,id)->openRow(pos));
    list.setOnItemLongClickListener((a,v,pos,id)->{toggleSelection(pos);return true;});
    LinearLayout actions=new LinearLayout(this);
    moveButton=button("Mover selecionados");packButton=button("Compactar ZIP");
    actions.addView(moveButton,new LinearLayout.LayoutParams(0,-2,1));
    actions.addView(packButton,new LinearLayout.LayoutParams(0,-2,1));base.addView(actions);
    moveButton.setOnClickListener(v->startMove());packButton.setOnClickListener(v->startPack());
    extract=button("Extrair ZIP em nova pasta...");base.addView(extract);
    extract.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Extrair ZIP")
      .setMessage("O ZIP original será preservado. A extração cria uma nova subpasta no destino selecionado. Arquivos extraídos podem receber novas datas.")
      .setNegativeButton("Cancelar",null).setPositiveButton("Selecionar destino",(d,w)->chooseFolder(EXTRACT)).show());
    progressText=new TextView(this);progressText.setTextSize(12);base.addView(progressText);
    status=new TextView(this);status.setTextColor(Color.DKGRAY);status.setPadding(0,9,0,0);base.addView(status);
    setContentView(base);
    if(!restoreLastFolder())render("Escolha uma pasta ou um ZIP. Pressione e segure um arquivo para selecionar.");
  }
  Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);return b;}
  void chooseFolder(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
  void chooseZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,ZIP);}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri u=data.getData();try{
      if(request==FOLDER){
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(u,flags);
        root=u;docId=DocumentsContract.getTreeDocumentId(u);
        pathNames.clear();pathIds.clear();selected.clear();inZip=false;zipFile=null;saveLastFolder();refresh();}
      if(request==ZIP){getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);loadZip(u,queryName(u));}
      if(request==EXTRACT)extractZip(u);
      if(request==MOVE)moveSelected(u);
      if(request==COPY){
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if(flags!=0)getContentResolver().takePersistableUriPermission(u,flags);
        copySelected(u);
      }
      if(request==PACK){
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if(flags!=0)getContentResolver().takePersistableUriPermission(u,flags);
        packSelected(u);
      }
    }catch(Exception e){fail(e.getMessage());}
  }
  String queryName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "arquivo.zip";}
  void render(String s){path.setText(inZip?"ZIP: "+zipName+" / "+prefix:root==null?"Nenhuma pasta selecionada":"Pasta / "+String.join(" / ",pathNames));
    extract.setVisibility(inZip?View.VISIBLE:View.GONE);
    moveButton.setVisibility(inZip?View.GONE:View.VISIBLE);
    packButton.setVisibility(inZip?View.GONE:View.VISIBLE);
    moveButton.setEnabled(!busy&&!selected.isEmpty());packButton.setEnabled(!busy&&!selected.isEmpty());
    status.setText(s+(selected.isEmpty()?"":" • "+selected.size()+" selecionado(s)"));
    ArrayList<String> labels=new ArrayList<>();for(Row r:rows)labels.add((selected.contains(r.id)?"☑ ":"    ")+r.toString());list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,labels));
  }
  void refresh(){if(inZip){showZip();return;}if(root==null||docId==null){rows.clear();render("Abra uma pasta ou ZIP.");return;}if(busy)return;
    busy=true;status.setText("Lendo pasta...");Uri snapshot=root;String id=docId;
    worker.execute(()->{ArrayList<Row> out=new ArrayList<>();String err=null;
      try{Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(snapshot,id);
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        try(Cursor c=getContentResolver().query(children,projection,null,null,null)){
          if(c==null)throw new IOException("Pasta indisponível");
          while(c.moveToNext()){String entryId=c.getString(0),name=c.getString(1),mime=c.getString(2);long size=c.isNull(3)?-1:c.getLong(3);
            if(name==null||(!hidden&&name.startsWith(".")))continue;
            long modified=c.isNull(4)?-1:c.getLong(4);
            boolean isDir=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            boolean isZip=!isDir&&name.toLowerCase(Locale.ROOT).endsWith(".zip");
            if(!search.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
            Row entry=new Row(name,entryId,isDir?"Pasta":(isZip?"ZIP · ":"")+sizeString(size)+" · "+dateString(modified),isDir,isZip);
            entry.size=size;entry.modified=modified;entry.mime=mime;out.add(entry);
          }
        }
        sortRows(out);
      }catch(Exception e){err=e.getMessage();}
      String msg=err;runOnUiThread(()->{busy=false;if(inZip||!snapshot.equals(root)||!id.equals(docId))return;rows.clear();rows.addAll(out);render(msg==null?out.size()+" itens":"Erro: "+msg);});
    });
  }
  void sortRows(ArrayList<Row> a){a.sort((x,y)->{
    if(x.folder!=y.folder)return x.folder?-1:1;
    int cmp=0;
    switch(sortMode){case "size":cmp=Long.compare(x.size,y.size);break;
      case "date":cmp=Long.compare(x.modified,y.modified);break;
      case "type":cmp=String.valueOf(x.mime).compareToIgnoreCase(String.valueOf(y.mime));break;
      default:cmp=x.name.compareToIgnoreCase(y.name);}
    if(cmp==0)cmp=x.name.compareToIgnoreCase(y.name);
    return reverse?-cmp:cmp;
  });}
  static String dateString(long n){return n<=0?"sem data":new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(n));}
  void openRow(int pos){if(busy||pos<0||pos>=rows.size())return;Row r=rows.get(pos);
    if(inZip){if(r.folder){prefix=r.id;showZip();}else new AlertDialog.Builder(this).setTitle(r.name).setMessage("Arquivo dentro do ZIP.\n"+r.note+"\n\nNenhuma extração foi feita.").setPositiveButton("OK",null).show();}
    else if(r.folder){history.add("/"+String.join("/",pathNames));pathNames.add(r.name);pathIds.add(r.id);docId=r.id;selected.clear();saveLastFolder();refresh();}
    else if(r.zip)loadZip(DocumentsContract.buildDocumentUriUsingTree(root,r.id),r.name);
    else fileActions(r);
  }
  void loadZip(Uri uri,String name){if(busy)return;busy=true;status.setText("Lendo ZIP sem extrair...");
    worker.execute(()->{ArrayList<ZItem> out=new ArrayList<>();String err=null;
      try(InputStream input=getContentResolver().openInputStream(uri)){
        if(input==null)throw new IOException("Acesso ao arquivo negado");
        try(ZipInputStream zip=new ZipInputStream(input)){
          ZipEntry e;int count=0;while((e=zip.getNextEntry())!=null){if(++count>12000)throw new IOException("Mais de 12 mil entradas no ZIP");String entry=e.getName().replace('\\','/');
            while(entry.startsWith("./"))entry=entry.substring(2);
            if(!entry.isEmpty())out.add(new ZItem(entry,e.isDirectory(),e.getSize()));zip.closeEntry();
          }
        }
      }catch(Exception e){err=e.getMessage();}
      String msg=err;runOnUiThread(()->{busy=false;if(msg!=null){fail("Não foi possível ler ZIP: "+msg);return;}zipFile=uri;zipName=name;selected.clear();zipItems.clear();zipItems.addAll(out);prefix="";inZip=true;showZip();});
    });
  }
  void showZip(){if(!inZip)return;Map<String,Row> opts=new HashMap<>();
    for(ZItem z:zipItems){if(!z.name.startsWith(prefix)||z.name.equals(prefix))continue;
      String rest=z.name.substring(prefix.length());int slash=rest.indexOf('/');
      String seg=slash<0?rest:rest.substring(0,slash);
      if(seg.isEmpty()||(!hidden&&seg.startsWith(".")))continue;
      boolean folder=slash>=0||z.folder;
      opts.put(seg,new Row(seg,folder?prefix+seg+"/":z.name,folder?"Pasta interna":sizeString(z.size),folder,false));
    }
    rows.clear();rows.addAll(opts.values());sortRows(rows);render(rows.size()+" itens • "+zipItems.size()+" entradas no ZIP");
  }
  void goBack(){if(busy)return;
    if(inZip){if(!prefix.isEmpty()){String p=prefix.substring(0,prefix.length()-1);int i=p.lastIndexOf('/');prefix=i<0?"":p.substring(0,i+1);showZip();}
      else {inZip=false;zipFile=null;zipItems.clear();refresh();}}
    else if(!pathNames.isEmpty()){pathNames.remove(pathNames.size()-1);pathIds.remove(pathIds.size()-1);docId=pathIds.isEmpty()?DocumentsContract.getTreeDocumentId(root):pathIds.get(pathIds.size()-1);selected.clear();saveLastFolder();refresh();}
    else Toast.makeText(this,"Você está na pasta inicial",Toast.LENGTH_SHORT).show();
  }
  static String sizeString(long n){if(n<0)return "Tamanho desconhecido";if(n<1024)return n+" B";return String.format(Locale.ROOT,n<1048576?"%.1f KB":"%.1f MB",n<(1048576)?n/1024.0:n/1048576.0);}
  void fail(String s){status.setText(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onBackPressed(){goBack();}
  @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
  static boolean safe(String name){
    if(name.startsWith("/")||name.startsWith("\\")||name.contains(":")||name.indexOf('\0')>=0)return false;
    for(String segment:name.split("/"))if(segment.equals(".."))return false;
    return true;
  }
  void extractZip(Uri destination){if(busy||zipFile==null)return;
    Uri source=zipFile;String title=zipName;busy=true;status.setText("Extraindo... Não feche o aplicativo.");
    worker.execute(()->{
      String err=null;int files=0;long bytes=0;
      try{
        android.content.ContentResolver resolver=getContentResolver();
        Uri rootDoc=DocumentsContract.buildDocumentUriUsingTree(destination,DocumentsContract.getTreeDocumentId(destination));
        String base=title.replaceAll("(?i)\\.zip$","").replaceAll("[^A-Za-z0-9._ -]","_");
        if(base.isEmpty()||base.equals(".")||base.equals(".."))base="arquivo";
        Uri output=DocumentsContract.createDocument(resolver,rootDoc,DocumentsContract.Document.MIME_TYPE_DIR,base+"_extraido_"+System.currentTimeMillis());
        if(output==null)throw new IOException("Não foi possível criar a pasta de destino");
        Map<String,Uri> made=new HashMap<>();made.put("",output);
        try(InputStream in=resolver.openInputStream(source)){
          if(in==null)throw new IOException("Não foi possível ler ZIP");
          try(ZipInputStream zip=new ZipInputStream(in)){
            byte[] buffer=new byte[32768];ZipEntry e;int entryCount=0;
            while((e=zip.getNextEntry())!=null){
              if(++entryCount>12000)throw new IOException("Limite de itens excedido");
              String name=e.getName().replace('\\','/');while(name.startsWith("./"))name=name.substring(2);
              if(name.isEmpty()){zip.closeEntry();continue;}
              if(!safe(name))throw new IOException("Caminho inseguro: "+name);
              String[] pieces=name.split("/");String parent="";
              int subCount=e.isDirectory()?pieces.length:pieces.length-1;
              for(int k=0;k<subCount;k++){
                if(pieces[k].isEmpty())continue;
                String child=parent+pieces[k]+"/";
                if(!made.containsKey(child)){
                  Uri newDir=DocumentsContract.createDocument(resolver,made.get(parent),DocumentsContract.Document.MIME_TYPE_DIR,pieces[k]);
                  if(newDir==null)throw new IOException("Erro criando pasta: "+child);
                  made.put(child,newDir);
                }
                parent=child;
              }
              if(!e.isDirectory()){
                String fileName=pieces[pieces.length-1];
                if(fileName.isEmpty())throw new IOException("Nome vazio");
                Uri file=DocumentsContract.createDocument(resolver,made.get(parent),"application/octet-stream",fileName);
                if(file==null)throw new IOException("Erro criando arquivo: "+name);
                try(OutputStream out=resolver.openOutputStream(file,"w")){
                  if(out==null)throw new IOException("Falha ao abrir arquivo de saída");
                  long fileBytes=0;int length;
                  while((length=zip.read(buffer))!=-1){
                    fileBytes+=length;bytes+=length;
                    if(fileBytes>150L*1024*1024||bytes>500L*1024*1024)throw new IOException("Limite de extração (150 MB por arquivo, 500 MB no total)");
                    out.write(buffer,0,length);
                  }
                }
                files++;
              }
              zip.closeEntry();
            }
          }
        }
      }catch(Exception e){err=e.getMessage();}
      final int count=files;final long total=bytes;final String message=err;
      runOnUiThread(()->{busy=false;
        if(message==null){render("Extraídos "+count+" arquivos ("+sizeString(total)+")");
          new AlertDialog.Builder(this).setTitle("Extração concluída").setMessage("Extraídos "+count+" arquivos numa nova subpasta. ZIP original preservado. Datas de arquivos extraídos podem mudar.").setPositiveButton("OK",null).show();}
        else{render("Falha: "+message);
          new AlertDialog.Builder(this).setTitle("Extração incompleta").setMessage(message+"\n\nUma pasta parcialmente extraída pode permanecer no destino.").setPositiveButton("OK",null).show();}
      });
    });
  }

  // O Android preserva a autorização da árvore; este método recupera também a subpasta atual.
  void saveLastFolder(){
    if(root==null||docId==null)return;
    prefs.edit().putString("tree",root.toString()).putString("doc",docId)
      .putString("names",new org.json.JSONArray(pathNames).toString())
      .putString("ids",new org.json.JSONArray(pathIds).toString()).apply();
  }
  boolean restoreLastFolder(){
    String value=prefs.getString("tree",null);if(value==null)return false;
    try{
      Uri stored=Uri.parse(value);
      boolean granted=false;
      for(android.content.UriPermission perm:getContentResolver().getPersistedUriPermissions()){
        if(perm.getUri().equals(stored)&&perm.isReadPermission()){granted=true;break;}
      }
      if(!granted)return false;
      root=stored;docId=prefs.getString("doc",DocumentsContract.getTreeDocumentId(stored));
      org.json.JSONArray names=new org.json.JSONArray(prefs.getString("names","[]"));
      org.json.JSONArray ids=new org.json.JSONArray(prefs.getString("ids","[]"));
      pathNames.clear();pathIds.clear();
      for(int i=0;i<Math.min(names.length(),ids.length());i++){
        pathNames.add(names.getString(i));pathIds.add(ids.getString(i));
      }
      refresh();return true;
    }catch(Exception e){root=null;docId=null;return false;}
  }
  void toggleSelection(int pos){
    if(inZip){Toast.makeText(this,"Seleção disponível nas pastas comuns",Toast.LENGTH_SHORT).show();return;}
    if(busy||pos<0||pos>=rows.size())return;
    Row r=rows.get(pos);

    if(!selected.add(r.id))selected.remove(r.id);
    render("Pressione e segure para marcar/desmarcar; toque normal para abrir.");
  }
  ArrayList<Row> selectedFiles(){
    ArrayList<Row> out=new ArrayList<>();
    for(Row r:rows)if(selected.contains(r.id))out.add(r);
    return out;
  }
  void startMove(){
    if(inZip||selectedFiles().isEmpty()){fail("Selecione arquivos com um toque prolongado.");return;}
    new AlertDialog.Builder(this).setTitle("Mover arquivos")
      .setMessage("Solicitaremos o MOVE nativo ao provedor Android. Não há fallback de copiar e apagar. A operação pode falhar se o provedor ou a pasta não permitirem mover. Arquivos de mesmo nome exigem cuidado.")
      .setNegativeButton("Cancelar",null).setPositiveButton("Escolher pasta destino",(d,w)->chooseFolder(MOVE)).show();
  }
  void moveSelected(Uri destination){
    if(root==null||docId==null)return;
    final Uri sourceTree=root;final String parentId=docId;
    final ArrayList<Row> targets=selectedFiles();
    if(targets.isEmpty())return;
    final Uri parentDoc=DocumentsContract.buildDocumentUriUsingTree(sourceTree,parentId);
    final Uri destinationDoc=DocumentsContract.buildDocumentUriUsingTree(destination,DocumentsContract.getTreeDocumentId(destination));
    busy=true;render("Movendo arquivos...");
    worker.execute(()->{
      int moved=0;ArrayList<String> errors=new ArrayList<>();
      for(Row r:targets){
        try{
          Uri document=DocumentsContract.buildDocumentUriUsingTree(sourceTree,r.id);
          Uri result=DocumentsContract.moveDocument(getContentResolver(),document,parentDoc,destinationDoc);
          if(result==null)throw new IOException("MOVE não suportado pelo provedor");
          moved++;
        }catch(Exception e){errors.add(r.name+": "+e.getClass().getSimpleName()+" — "+e.getMessage());}
      }
      final int n=moved;final String problem=String.join("\n",errors);
      runOnUiThread(()->{
        busy=false;selected.clear();refresh();
        new AlertDialog.Builder(this).setTitle("Resultado da movimentação")
          .setMessage(n+" de "+targets.size()+" arquivo(s) movido(s) pelo pedido nativo."
            +(problem.isEmpty()?"":"\n\nFalhas:\n"+problem)
            +"\n\nAs datas dependem do provedor Android.")
          .setPositiveButton("OK",null).show();
      });
    });
  }
  void startPack(){
    if(inZip||selectedFiles().isEmpty()){fail("Selecione arquivos com um toque prolongado.");return;}
    new AlertDialog.Builder(this).setTitle("Criar arquivo ZIP")
      .setMessage("Compactar "+selectedFiles().size()+" item(ns), incluindo pastas e ocultos? Os originais permanecem intactos.")
      .setNegativeButton("Cancelar",null).setPositiveButton("Escolher pasta destino",(d,w)->chooseFolder(PACK)).show();
  }
  void packSelected(Uri destination){
    if(root==null)return;
    final Uri tree=root;final ArrayList<Row> targets=selectedFiles();if(targets.isEmpty())return;
    final Uri targetDoc=DocumentsContract.buildDocumentUriUsingTree(destination,DocumentsContract.getTreeDocumentId(destination));
    busy=true;cancelTask=false;render("Criando ZIP...");
    worker.execute(()->{
      String err=null;int count=0;Uri created=null;
      try{
        String filename="Projeto_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".zip";
        created=DocumentsContract.createDocument(getContentResolver(),targetDoc,"application/zip",filename);
        if(created==null)throw new IOException("Não foi possível criar ZIP no destino");
        try(OutputStream out=getContentResolver().openOutputStream(created,"w")){
          if(out==null)throw new IOException("Destino sem escrita");
          try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out))){
            zip.setLevel(6);long[] totals={0,0};Set<String> names=new HashSet<>();
            for(Row row:targets){
              Uri item=DocumentsContract.buildDocumentUriUsingTree(tree,row.id);
              String name=includeContainer?"Projeto/"+row.name:row.name;
              writeZipItem(zip,item,name,0,totals,names);
            }
            count=(int)totals[0];
          }
        }
      }catch(Exception e){err=e.getMessage();if(created!=null)try{DocumentsContract.deleteDocument(getContentResolver(),created);}catch(Exception ignored){}}
      final int n=count;final String error=err;
      runOnUiThread(()->{busy=false;selected.clear();refresh();
        new AlertDialog.Builder(this).setTitle(error==null?"ZIP criado":"Falha ao compactar")
          .setMessage(error==null?n+" arquivo(s) incluído(s). Originais preservados. "+(includeContainer?"Com pasta principal.":"Sem pasta principal.")
            :error+"\nUm ZIP incompleto foi removido quando possível.")
          .setPositiveButton("OK",null).show();
      });
    });
  }
  void writeZipItem(ZipOutputStream zip,Uri doc,String name,int depth,long[] totals,Set<String> names)throws Exception{
    if(depth>35||totals[0]>25000||totals[1]>1024L*1024*1024)throw new IOException("Limites do ZIP excedidos");
    if(!safe(name)||name.contains("\\"))throw new IOException("Caminho inseguro: "+name);
    String mime=getMime(doc);
    boolean dir=DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
    if(dir){
      String d=name.endsWith("/")?name:name+"/";
      if(names.add(d)){zip.putNextEntry(new ZipEntry(d));zip.closeEntry();}
      String id=DocumentsContract.getDocumentId(doc);
      Uri kids=DocumentsContract.buildChildDocumentsUriUsingTree(root,id);
      try(Cursor c=getContentResolver().query(kids,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
        if(c==null)throw new IOException("Não foi possível listar "+name);
        while(c.moveToNext()){
          String childName=c.getString(1),childId=c.getString(0);
          if(childName==null||childName.equals(".")||childName.equals("..")||childName.contains("/"))continue;
          writeZipItem(zip,DocumentsContract.buildDocumentUriUsingTree(root,childId),d+childName,depth+1,totals,names);
        }
      }
    }else{
      if(!names.add(name))throw new IOException("Entrada duplicada: "+name);
      try(InputStream in=getContentResolver().openInputStream(doc)){
        if(in==null)throw new IOException("Sem acesso: "+name);
        ZipEntry entry=new ZipEntry(name);zip.putNextEntry(entry);byte[] buf=new byte[32768];int n;
        while((n=in.read(buf))!=-1){totals[1]+=n;if(totals[1]>1024L*1024*1024)throw new IOException("Limite de 1 GB");zip.write(buf,0,n);}
        zip.closeEntry();totals[0]++;
      }
    }
  }
  String getMime(Uri u){
    try(Cursor c=getContentResolver().query(u,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
      if(c!=null&&c.moveToFirst())return c.getString(0);
    }catch(Exception ignored){}return "application/octet-stream";
  }
  void showOperations(){
    String[] ops={"Criar pasta","Criar arquivo vazio","Renomear selecionado","Copiar selecionados","Mover selecionados","Excluir selecionados","Detalhes selecionados","Compartilhar selecionados","Abrir/editar arquivo de texto"};
    new AlertDialog.Builder(this).setTitle("Gerenciar arquivos").setItems(ops,(d,w)->{
      switch(w){case 0:createItem(true);break;case 1:createItem(false);break;
        case 2:renameSelected();break;case 3:if(!selected.isEmpty())chooseFolder(COPY);else fail("Marque um arquivo ou pasta");break;
        case 4:startMove();break;case 5:deleteSelected();break;case 6:detailsSelected();break;
        case 7:shareSelected();break;case 8:editSelected();break;}
    }).show();
  }
  void showArchiveTools(){
    new AlertDialog.Builder(this).setTitle("Compactação")
      .setSingleChoiceItems(new String[]{"ZIP sem pasta principal (ZiptoGit)","ZIP com pasta principal Projeto/"},includeContainer?1:0,
        (d,w)->{includeContainer=w==1;d.dismiss();startPack();})
      .setNegativeButton("Cancelar",null).show();
  }
  void createItem(boolean directory){
    if(root==null||inZip){fail("Abra uma pasta normal");return;}
    prompt(directory?"Nome da pasta":"Nome do arquivo",directory?"NovaPasta":"novo.txt",value->{
      if(!validFilename(value))return;
      runTask("Criando...",()->{
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(root,docId);
        Uri made=DocumentsContract.createDocument(getContentResolver(),parent,directory?DocumentsContract.Document.MIME_TYPE_DIR:"text/plain",value);
        if(made==null)throw new IOException("Provedor recusou criação");
      });
    });
  }
  interface TextReceiver{void accept(String value);}
  void prompt(String title,String initial,TextReceiver action){
    EditText field=new EditText(this);field.setSingleLine(true);field.setText(initial);field.setSelectAllOnFocus(true);
    LinearLayout wrap=new LinearLayout(this);wrap.setPadding(24,8,24,4);wrap.addView(field);
    new AlertDialog.Builder(this).setTitle(title).setView(wrap).setNegativeButton("Cancelar",null)
      .setPositiveButton("OK",(d,w)->action.accept(field.getText().toString().trim())).show();
  }
  boolean validFilename(String name){
    if(name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\")){fail("Nome inválido");return false;}return true;
  }
  void runTask(String message,Task task){
    if(busy)return;busy=true;render(message);
    worker.execute(()->{String error=null;try{task.run();}catch(Exception e){error=e.getMessage();}
      String result=error;runOnUiThread(()->{busy=false;selected.clear();refresh();Toast.makeText(this,result==null?"Concluído":("Erro: "+result),Toast.LENGTH_LONG).show();});
    });
  }
  interface Task{void run()throws Exception;}
  void renameSelected(){
    ArrayList<Row> t=selectedFiles();if(t.size()!=1){fail("Marque apenas um item");return;}
    Row r=t.get(0);prompt("Renomear",r.name,name->{if(!validFilename(name))return;
      runTask("Renomeando...",()->{
        Uri item=DocumentsContract.buildDocumentUriUsingTree(root,r.id);
        if(DocumentsContract.renameDocument(getContentResolver(),item,name)==null)throw new IOException("Renomeação não suportada");
      });
    });
  }
  void deleteSelected(){
    ArrayList<Row> t=selectedFiles();if(t.isEmpty()){fail("Marque os itens");return;}
    new AlertDialog.Builder(this).setTitle("Excluir definitivamente?").setMessage("Excluir "+t.size()+" item(ns), inclusive conteúdo de pastas? Pode ser irreversível.")
      .setNegativeButton("Cancelar",null).setPositiveButton("EXCLUIR",(d,w)->runTask("Excluindo...",()->{
        for(Row r:t){Uri item=DocumentsContract.buildDocumentUriUsingTree(root,r.id);
          if(!DocumentsContract.deleteDocument(getContentResolver(),item))throw new IOException("Falhou: "+r.name);}
      })).show();
  }
  void copySelected(Uri dest){
    ArrayList<Row> t=selectedFiles();if(t.isEmpty())return;
    Uri parent=DocumentsContract.buildDocumentUriUsingTree(dest,DocumentsContract.getTreeDocumentId(dest));
    runTask("Copiando...",()->{
      for(Row r:t){Uri item=DocumentsContract.buildDocumentUriUsingTree(root,r.id);
        Uri result=DocumentsContract.copyDocument(getContentResolver(),item,parent);
        if(result==null)throw new IOException("Cópia não suportada para: "+r.name);
      }
    });
  }
  void detailsSelected(){
    ArrayList<Row> t=selectedFiles();if(t.size()!=1){fail("Marque um item");return;}
    Row r=t.get(0);
    new AlertDialog.Builder(this).setTitle(r.name).setMessage("Tipo: "+r.mime+"\nTamanho: "+sizeString(r.size)+"\nModificado: "+dateString(r.modified)+"\nID: "+r.id)
      .setPositiveButton("OK",null).show();
  }
  void shareSelected(){
    ArrayList<Row> t=selectedFiles();if(t.isEmpty()){fail("Marque arquivos");return;}
    ArrayList<Uri> links=new ArrayList<>();for(Row r:t)if(!r.folder)links.add(DocumentsContract.buildDocumentUriUsingTree(root,r.id));
    if(links.isEmpty()){fail("Selecione arquivos normais");return;}
    Intent intent=new Intent(links.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);
    intent.setType("*/*");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    if(links.size()==1)intent.putExtra(Intent.EXTRA_STREAM,links.get(0));else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,links);
    startActivity(Intent.createChooser(intent,"Compartilhar"));
  }
  void editSelected(){ArrayList<Row> t=selectedFiles();if(t.size()!=1||t.get(0).folder){fail("Marque um arquivo de texto");return;}editText(t.get(0));}
  void fileActions(Row r){
    String[] choices={"Abrir como texto / editar","Detalhes","Compartilhar","Visualizar imagem"};
    new AlertDialog.Builder(this).setTitle(r.name).setItems(choices,(d,which)->{
      if(which==0)editText(r);else if(which==1){selected.clear();selected.add(r.id);detailsSelected();}
      else if(which==2){selected.clear();selected.add(r.id);shareSelected();}
      else viewImage(r);
    }).show();
  }
  void viewImage(Row r){
    Uri uri=DocumentsContract.buildDocumentUriUsingTree(root,r.id);
    worker.execute(()->{
      Bitmap bmp=null;String error=null;
      try(InputStream in=getContentResolver().openInputStream(uri)){
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=2;bmp=BitmapFactory.decodeStream(in,null,opt);
        if(bmp==null)throw new IOException("Formato de imagem indisponível");
      }catch(Exception e){error=e.getMessage();}
      Bitmap image=bmp;String problem=error;
      runOnUiThread(()->{if(problem!=null){fail(problem);return;}
        ImageView v=new ImageView(this);v.setImageBitmap(image);v.setAdjustViewBounds(true);
        new AlertDialog.Builder(this).setTitle(r.name).setView(v).setPositiveButton("Fechar",null).show();});
    });
  }
  void editText(Row r){
    Uri uri=DocumentsContract.buildDocumentUriUsingTree(root,r.id);
    worker.execute(()->{
      String content=null,error=null;
      try(InputStream in=getContentResolver().openInputStream(uri)){
        ByteArrayOutputStream bout=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(bout.size()+n>1024*1024)throw new IOException("Editor limitado a 1 MB");bout.write(buf,0,n);}
        content=bout.toString("UTF-8");
      }catch(Exception e){error=e.getMessage();}
      String finalContent=content,problem=error;
      runOnUiThread(()->{if(problem!=null){fail(problem);return;}
        EditText editor=new EditText(this);editor.setGravity(Gravity.TOP);editor.setTextSize(13);
        editor.setTypeface(android.graphics.Typeface.MONOSPACE);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setText(finalContent);ScrollView sc=new ScrollView(this);sc.addView(editor);
        new AlertDialog.Builder(this).setTitle("Editar "+r.name).setView(sc)
          .setNegativeButton("Fechar",null).setNeutralButton("Salvar cópia",(d,w)->{
            String newContent=editor.getText().toString();
            prompt("Nome da cópia",r.name+".copia.txt",name->{if(!validFilename(name))return;
              runTask("Salvando cópia...",()->{
                Uri parent=DocumentsContract.buildDocumentUriUsingTree(root,docId);
                Uri created=DocumentsContract.createDocument(getContentResolver(),parent,"text/plain",name);
                if(created==null)throw new IOException("Não foi possível criar arquivo");
                try(OutputStream o=getContentResolver().openOutputStream(created,"w")){o.write(newContent.getBytes("UTF-8"));}
              });
            });
          }).setPositiveButton("Salvar",(d,w)->{
            String val=editor.getText().toString();
            new AlertDialog.Builder(this).setTitle("Sobrescrever arquivo?").setMessage("Alterar "+r.name+"? Essa operação muda a data de modificação.")
              .setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(dialog,which)->runTask("Salvando...",()->{
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                  if(out==null)throw new IOException("Sem permissão de escrita");out.write(val.getBytes("UTF-8"));
                }
              })).show();
          }).show();
      });
    });
  }
  void searchDialog(){prompt("Buscar nesta pasta (nome)",search,value->{search=value;refresh();});}
  void sortDialog(){String[] opts={"Nome","Tamanho","Data de modificação","Tipo","Inverter ordem"};
    new AlertDialog.Builder(this).setTitle("Ordenação").setItems(opts,(d,w)->{
      if(w==4)reverse=!reverse;else sortMode=new String[]{"name","size","date","type"}[w];
      prefs.edit().putString("sort",sortMode).putBoolean("reverse",reverse).apply();refresh();
    }).show();
  }
  void selectDialog(){String[] opts={"Selecionar todos","Desmarcar todos","Inverter seleção","Selecionar por extensão"};
    new AlertDialog.Builder(this).setTitle("Seleção").setItems(opts,(d,w)->{
      if(w==0){for(Row r:rows)selected.add(r.id);render("Tudo selecionado");}
      if(w==1){selected.clear();render("Seleção limpa");}
      if(w==2){for(Row r:rows)if(!selected.add(r.id))selected.remove(r.id);render("Seleção invertida");}
      if(w==3)prompt("Extensão (ex.: .txt)",".zip",value->{for(Row r:rows)if(r.name.toLowerCase(Locale.ROOT).endsWith(value.toLowerCase(Locale.ROOT)))selected.add(r.id);render("Selecionado por extensão");});
    }).show();
  }
  void showLocations(){
    if(root==null){chooseFolder(FOLDER);return;}
    String now=root.toString();
    String[] opts={"★ Favoritar pasta principal","Abrir pasta favorita","Voltar à pasta principal","Selecionar outra pasta","Histórico recente"};
    new AlertDialog.Builder(this).setTitle("Locais e favoritos").setItems(opts,(d,w)->{
      if(w==0){favorites.add(now);prefs.edit().putString("favorites",new org.json.JSONArray(favorites).toString()).apply();Toast.makeText(this,"Favorita salva",Toast.LENGTH_SHORT).show();}
      if(w==1){String[] urls=favorites.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("Pastas favoritas").setItems(urls,(a,b)->{
        root=Uri.parse(urls[b]);docId=DocumentsContract.getTreeDocumentId(root);pathIds.clear();pathNames.clear();saveLastFolder();refresh();
      }).show();}
      if(w==2){docId=DocumentsContract.getTreeDocumentId(root);pathIds.clear();pathNames.clear();saveLastFolder();refresh();}
      if(w==3)chooseFolder(FOLDER);
      if(w==4)new AlertDialog.Builder(this).setTitle("Histórico da sessão").setItems(history.toArray(new String[0]),(a,b)->Toast.makeText(this,history.get(b),Toast.LENGTH_LONG).show()).show();
    }).show();
  }

}
