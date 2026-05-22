// SmartLibrary.java
// Corrected version for Java 21 (no text-block concatenation errors)

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/*
 Single-file SmartLibrary:
 - Java 21 text blocks used safely with .formatted(...)
 - Session token S1
 - Dark D2 theme, nav, add/view/issue/return/history, categories, search
 - Persistent text files: users.txt, books.txt, issued.txt
*/

public class SmartLibrary {
    static final String USERS_FILE = "users.txt";
    static final String BOOKS_FILE = "books.txt";
    static final String ISSUED_FILE = "issued.txt";

    static final Map<String, String> sessions = new HashMap<>();
    static final Map<String, User> users = new HashMap<>();
    static final Map<Integer, Book> books = new LinkedHashMap<>();
    static final Map<Integer, Issue> issues = new LinkedHashMap<>();
    static int nextBookId = 1;
    static int nextIssueId = 1;
    static final int LOAN_DAYS = 14;
    static final int FINE_PER_DAY = 1;

    static class User { String username, password; User(String u,String p){username=u;password=p;} }
    static class Book { int id; String title, author, category; boolean available; Book(int i,String t,String a,String c,boolean av){id=i;title=t;author=a;category=c;available=av;} }
    static class Issue { int iid, bookId; String username; LocalDate issuedOn; LocalDate returnedOn; Issue(int i,int b,String u,LocalDate d){iid=i;bookId=b;username=u;issuedOn=d;returnedOn=null;} }

    // ---------- I/O ----------
    static void loadUsers(){
        try{ File f=new File(USERS_FILE); if(!f.exists()) return;
            try(var br=new BufferedReader(new FileReader(f))){
                String ln; while((ln=br.readLine())!=null){
                    String[] p=ln.split(",",2); if(p.length<2) continue;
                    users.put(p[0], new User(p[0], p[1]));
                }
            }
        }catch(Exception e){ }
    }
    static void saveUsers(){ try(var pw=new PrintWriter(new FileWriter(USERS_FILE))){ for(var u: users.values()) pw.println(u.username + "," + u.password); } catch(Exception e){} }

    static void loadBooks(){
        try{ File f=new File(BOOKS_FILE); if(!f.exists()) return;
            try(var br=new BufferedReader(new FileReader(f))){
                String ln; while((ln=br.readLine())!=null){
                    String[] p=ln.split(",",5); if(p.length<5) continue;
                    int id=Integer.parseInt(p[0]);
                    books.put(id, new Book(id, decode(p[1]), decode(p[2]), decode(p[3]), p[4].equals("1")));
                    nextBookId = Math.max(nextBookId, id+1);
                }
            }
        }catch(Exception e){}
    }
    static void saveBooks(){ try(var pw=new PrintWriter(new FileWriter(BOOKS_FILE))){ for(var b: books.values()) pw.println(b.id + "," + encode(b.title) + "," + encode(b.author) + "," + encode(b.category) + "," + (b.available? "1":"0")); } catch(Exception e){} }

    static void loadIssues(){
        try{ File f=new File(ISSUED_FILE); if(!f.exists()) return;
            try(var br=new BufferedReader(new FileReader(f))){
                String ln; while((ln=br.readLine())!=null){
                    String[] p=ln.split(",",5); if(p.length<5) continue;
                    int iid=Integer.parseInt(p[0]); int bid=Integer.parseInt(p[1]);
                    Issue is=new Issue(iid,bid,p[2], LocalDate.parse(p[3]));
                    if(!p[4].equals("null")) is.returnedOn = LocalDate.parse(p[4]);
                    issues.put(iid, is);
                    nextIssueId = Math.max(nextIssueId, iid+1);
                }
            }
        }catch(Exception e){}
    }
    static void saveIssues(){ try(var pw=new PrintWriter(new FileWriter(ISSUED_FILE))){ for(var r: issues.values()) pw.println(r.iid + "," + r.bookId + "," + r.username + "," + r.issuedOn + "," + (r.returnedOn==null?"null":r.returnedOn)); } catch(Exception e){} }

    static void loadAll(){ loadUsers(); loadBooks(); loadIssues(); }

    // ---------- helpers ----------
    static String encode(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String decode(String s){ return URLDecoder.decode(s, StandardCharsets.UTF_8); }
    static Map<String,String> parseForm(String raw){
        var map = new HashMap<String,String>();
        if(raw==null || raw.isEmpty()) return map;
        for(String part: raw.split("&")){
            String[] kv = part.split("=",2);
            String k = decode(kv[0]);
            String v = kv.length>1 ? decode(kv[1]) : "";
            map.put(k,v);
        }
        return map;
    }
    static Map<String,String> parseQuery(String q){
        var map = new HashMap<String,String>(); if(q==null) return map;
        for(String part: q.split("&")){
            String[] kv = part.split("=",2);
            String k = kv[0];
            String v = kv.length>1 ? kv[1] : "";
            map.put(k,v);
        }
        return map;
    }

    static String createSession(String user){
        for(var e: sessions.entrySet()) if(e.getValue().equals(user)) return e.getKey();
        String t = UUID.randomUUID().toString();
        sessions.put(t, user);
        return t;
    }
    static String getUserFromSession(HttpExchange ex){
        try{
            String q = ex.getRequestURI().getQuery(); if(q==null) return null;
            var m = parseQuery(q); String s = m.get("session"); if(s==null) return null;
            return sessions.get(s);
        }catch(Exception e){return null;}
    }

    // ---------- Templates: use formatted(...) to inject variables ----------
    static final String CSS = """
        <style>
        :root{--bg:#0d1b2a;--card:#1b263b;--accent:#415a77;--accent2:#778da9;--muted:#a8dadc}
        body{background:var(--bg);color:#fff;font-family:Arial,Helvetica,sans-serif;margin:0;padding:22px;}
        .container{max-width:1000px;margin:0 auto;background:var(--card);padding:18px;border-radius:12px;box-shadow:0 10px 30px rgba(0,0,0,0.6)}
        .nav{display:flex;gap:12px;align-items:center;padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.03);margin-bottom:16px}
        .nav a{color:var(--muted);text-decoration:none;padding:8px 12px;border-radius:8px;font-weight:600}
        .nav a:hover{background:linear-gradient(90deg, rgba(65,90,119,0.2), rgba(119,141,169,0.12));color:#fff}
        h1{color:#87cefa;margin:6px 0 16px 0}
        .card{background:linear-gradient(180deg, rgba(255,255,255,0.02), rgba(0,0,0,0.04));padding:14px;border-radius:10px}
        input, select{width:100%;padding:10px;border-radius:8px;border:1px solid rgba(255,255,255,0.06);background:rgba(255,255,255,0.02);color:#fff;font-size:15px}
        button.btn{background:var(--accent);color:#fff;padding:10px 16px;border:none;border-radius:8px;cursor:pointer;font-weight:700}
        button.btn:hover{background:var(--accent2);transform:translateY(-2px);transition:all .18s}
        table{width:100%;border-collapse:collapse;margin-top:12px}
        th{background:var(--accent);padding:10px;text-align:center}
        td{padding:10px;text-align:center;border-bottom:1px solid rgba(255,255,255,0.03)}
        tr:hover td{background:rgba(255,255,255,0.02)}
        .muted{color:rgba(255,255,255,0.7);font-size:14px}
        .status-available{color:#b7f5c8;font-weight:700}
        .status-issued{color:#ffcccb;font-weight:700}
        .link{color:var(--muted);text-decoration:none}
        .topCard{display:flex;gap:12px;flex-wrap:wrap;justify-content:center;margin-top:18px}
        .cardAction{background:linear-gradient(90deg, rgba(255,255,255,0.03), rgba(0,0,0,0.05));padding:14px;border-radius:8px;min-width:160px;text-align:center}
        </style>
        """;

    static String topNav(String session){
        String sParam = session==null? "" : "?session=%s".formatted(session);
        String logoutOrLogin = session==null ? "<a href='/login' class='link'>Login</a>" : "<a href='/' class='link'>Logout</a>";
        String tpl = """
            <div class='nav'>
               <a href='/' class='link'>Home</a>
               <a href='/add%s' class='link'>Add</a>
               <a href='/view%s' class='link'>View</a>
               <a href='/issue%s' class='link'>Issue</a>
               <a href='/return%s' class='link'>Return</a>
               <a href='/history%s' class='link'>History</a>
               %s
            </div>
            """.formatted(sParam,sParam,sParam,sParam,sParam, logoutOrLogin);
        return tpl;
    }

    static String wrapPage(String body, String session){
        String html = """
            <html><head><meta charset='utf-8'><title>SmartLibrary</title>%s</head><body>
            <div class='container'>%s%s</div></body></html>
            """.formatted(CSS, topNav(session), body);
        return html;
    }

    // ---------- page fragments ----------
    static String homePage(){ 
        String body = """
            <div style='text-align:center'>
              <h1>Welcome to SmartLibrary</h1>
              <p class='muted'>Manage books, issue & return with a modern UI.</p>
              <div class='topCard'>
                <div class='cardAction'><a class='link' href='/login'>Login / Register</a><div class='muted'>Create account to start</div></div>
                <div class='cardAction'><a class='link' href='/view'>Browse Books</a><div class='muted'>See available titles</div></div>
              </div>
            </div>
            """;
        return wrapPage(body, null);
    }

    static String loginForm(){
        String body = """
            <div style='max-width:640px;margin:auto'><h1>Login</h1>
             <div class='card'>
               <form method='post' action='/login'>
                 <label class='muted'>Username</label><input name='username' type='text' required>
                 <label class='muted'>Password</label><div style='display:flex;gap:8px'><input id='pwd' name='password' type='password' style='flex:1'><button type='button' onclick='togg()' class='btn' style='background:#2b3b4a'>Show</button></div>
                 <br><button class='btn'>Login</button>
               </form>
               <p class='muted'>No account? <a class='link' href='/register'>Create one</a></p>
             </div></div>
            <script>function togg(){var p=document.getElementById('pwd'); p.type = p.type==='password'?'text':'password';}</script>
            """;
        return wrapPage(body, null);
    }

    static String registerForm(){
        String body = """
            <div style='max-width:640px;margin:auto'><h1>Create Account</h1>
              <div class='card'>
                <form method='post' action='/register'>
                  <label class='muted'>Choose username</label><input name='username' type='text' required>
                  <label class='muted'>Choose password</label><div style='display:flex;gap:8px'><input id='rpwd' name='password' type='password' style='flex:1'><button type='button' onclick='tr()' class='btn' style='background:#2b3b4a'>Show</button></div>
                  <br><button class='btn'>Create Account</button>
                </form>
              </div></div>
            <script>function tr(){var e=document.getElementById('rpwd'); e.type = e.type==='password'?'text':'password';}</script>
            """;
        return wrapPage(body, null);
    }

    static String dashboardPage(String user, String session){
        String body = """
            <h1 style='text-align:center;color:#87cefa'>Hello, %s</h1>
            <div class='topCard'>
              <div class='cardAction'><a class='link' href='/add?session=%s'>Add Book</a></div>
              <div class='cardAction'><a class='link' href='/view?session=%s'>View Books</a></div>
              <div class='cardAction'><a class='link' href='/issue?session=%s'>Issue Book</a></div>
              <div class='cardAction'><a class='link' href='/return?session=%s'>Return Book</a></div>
              <div class='cardAction'><a class='link' href='/history?session=%s'>My History</a></div>
            </div>
            """.formatted(escape(user), session, session, session, session, session);
        return wrapPage(body, session);
    }

    static String addBookForm(String session){
        String body = """
            <div style='max-width:760px;margin:auto'>
             <h1>Add New Book</h1>
             <div class='card'>
               <form method='post' action='/addbook'>
                 <input type='hidden' name='session' value='%s'>
                 <label class='muted'>Title</label><input name='title' type='text' required>
                 <label class='muted'>Author</label><input name='author' type='text' required>
                 <label class='muted'>Category</label>
                 <select name='category'><option>Novel</option><option>Science</option><option>Technology</option><option>History</option><option>Other</option></select><br><br>
                 <button class='btn'>Add Book</button>
               </form>
             </div>
            </div>
            """.formatted(escape(session));
        return wrapPage(body, session);
    }

    static String viewBooksPage(String session, String qFilter){
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Books</h1>");
        sb.append("<div class='card'>");
        sb.append("<form method='get' action='/view' style='display:flex;gap:8px;margin-bottom:8px'>");
        sb.append("<input name='q' placeholder='Search by ID, title or author' type='text' value='"+escape(qFilter==null?"":qFilter)+"'/>");
        if(session!=null) sb.append("<input type='hidden' name='session' value='"+session+"'>");
        sb.append("<button class='btn' type='submit'>Search</button></form>");
        sb.append("<table><tr><th>ID</th><th>Title</th><th>Author</th><th>Category</th><th>Status</th></tr>");
        for(var b: books.values()){
            boolean match=true;
            if(qFilter!=null && !qFilter.isBlank()){
                String q=qFilter.toLowerCase();
                match = (String.valueOf(b.id).contains(q) || b.title.toLowerCase().contains(q) || b.author.toLowerCase().contains(q));
            }
            if(!match) continue;
            sb.append("<tr><td>").append(b.id).append("</td><td>").append(escape(b.title)).append("</td><td>").append(escape(b.author)).append("</td><td>").append(escape(b.category)).append("</td><td>").append(b.available? "<span class='status-available'>Available</span>":"<span class='status-issued'>Issued</span>").append("</td></tr>");
        }
        sb.append("</table></div><br><a class='link' href='/dashboard?session="+(session==null?"":session)+"'>Back</a>");
        return wrapPage(sb.toString(), session);
    }

    static String issueForm(String session){
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Issue Book</h1><div class='card'><div class='muted'>Available books:</div>");
        sb.append("<table><tr><th>ID</th><th>Title</th><th>Author</th></tr>");
        for(var b: books.values()) if(b.available) sb.append("<tr><td>").append(b.id).append("</td><td>").append(escape(b.title)).append("</td><td>").append(escape(b.author)).append("</td></tr>");
        sb.append("</table><br>");
        sb.append("<form method='post' action='/issuebook'><input type='hidden' name='session' value='"+session+"'>Book ID: <input name='bookId' required> <button class='btn' type='submit'>Issue</button></form></div><br><a class='link' href='/dashboard?session="+session+"'>Back</a>");
        return wrapPage(sb.toString(), session);
    }

    static String returnForm(String session){
        String user = sessions.get(session);
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Return Book</h1><div class='card'><table><tr><th>IssueID</th><th>BookID</th><th>Title</th><th>Issued On</th><th>Action</th></tr>");
        for(var r: issues.values()){
            if(r.username.equals(user) && r.returnedOn==null){
                sb.append("<tr><td>").append(r.iid).append("</td><td>").append(r.bookId).append("</td><td>").append(escape(books.get(r.bookId).title)).append("</td><td>").append(r.issuedOn).append("</td><td><a class='link' href='/returnbook?iid=").append(r.iid).append("&session=").append(session).append("'>Return</a></td></tr>");
            }
        }
        sb.append("</table></div><br><a class='link' href='/dashboard?session="+session+"'>Back</a>");
        return wrapPage(sb.toString(), session);
    }

    static String historyPage(String session){
        String user = sessions.get(session);
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>My History</h1><div class='card'><table><tr><th>IID</th><th>Book</th><th>Issued On</th><th>Returned On</th><th>Fine</th></tr>");
        for(var r: issues.values()){
            if(r.username.equals(user)){
                long fine=0;
                if(r.returnedOn!=null){
                    long days=r.issuedOn.until(r.returnedOn).getDays();
                    fine = Math.max(0, days-LOAN_DAYS)*FINE_PER_DAY;
                } else {
                    long days=r.issuedOn.until(LocalDate.now()).getDays();
                    fine = Math.max(0, days-LOAN_DAYS)*FINE_PER_DAY;
                }
                sb.append("<tr><td>").append(r.iid).append("</td><td>").append(escape(books.get(r.bookId).title)).append("</td><td>").append(r.issuedOn).append("</td><td>").append(r.returnedOn==null? "-": r.returnedOn).append("</td><td>₹").append(fine).append("</td></tr>");
            }
        }
        sb.append("</table></div><br><a class='link' href='/dashboard?session="+session+"'>Back</a>");
        return wrapPage(sb.toString(), session);
    }

    // ---------- HTTP handlers ----------
    public static void main(String[] args) throws Exception {
        loadAll();
        HttpServer server = HttpServer.create(new InetSocketAddress(8000),0);

        server.createContext("/", ex -> send(ex, homePage()));

        server.createContext("/login", ex -> {
            if("GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, loginForm()); return; }
            String raw = readAll(ex);
            var f = parseForm(raw);
            String u = f.getOrDefault("username","").trim();
            String p = f.getOrDefault("password","");
            if(!users.containsKey(u) || !users.get(u).password.equals(p)){ send(ex, wrapPage("<h2 style='color:#ff6b6b'>Invalid credentials</h2><a class='link' href='/'>Back</a>", null)); return; }
            String token = createSession(u);
            redirect(ex, "/dashboard?session="+token);
        });

        server.createContext("/register", ex -> {
            if("GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, registerForm()); return; }
            String raw = readAll(ex);
            var f = parseForm(raw);
            String u = f.getOrDefault("username","").trim();
            String p = f.getOrDefault("password","");
            if(u.isEmpty()||p.isEmpty()){ send(ex, wrapPage("<h2 style='color:#ff6b6b'>Invalid data</h2><a class='link' href='/register'>Try</a>", null)); return; }
            if(users.containsKey(u)){ send(ex, wrapPage("<h2 style='color:#ff6b6b'>User exists</h2><a class='link' href='/register'>Try</a>", null)); return; }
            users.put(u,new User(u,p)); saveUsers();
            String token = createSession(u); redirect(ex, "/dashboard?session="+token);
        });

        server.createContext("/dashboard", ex -> {
            String user = getUserFromSession(ex);
            if(user==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            String token = parseQuery(ex.getRequestURI().getQuery()).get("session");
            send(ex, dashboardPage(user, token));
        });

        server.createContext("/add", ex -> {
            String token = parseQuery(ex.getRequestURI().getQuery()).get("session");
            String user = sessions.get(token);
            if(user==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            send(ex, addBookForm(token));
        });

        server.createContext("/addbook", ex -> {
            String raw = readAll(ex);
            var f = parseForm(raw);
            String token = f.get("session"); String user = sessions.get(token);
            if(user==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            String title = f.getOrDefault("title","").trim(); String author=f.getOrDefault("author","").trim(); String cat=f.getOrDefault("category","Other");
            if(title.isEmpty()||author.isEmpty()){ send(ex, wrapPage("<h2>Missing fields</h2><a class='link' href='/add?session="+token+"'>Back</a>", token)); return; }
            books.put(nextBookId, new Book(nextBookId, title, author, cat, true)); nextBookId++; saveBooks();
            redirect(ex, "/view?session="+token);
        });

        server.createContext("/view", ex -> {
            var qmap = parseQuery(ex.getRequestURI().getQuery());
            String q = qmap.get("q"); String token = qmap.get("session");
            send(ex, viewBooksPage(token, q==null?"":q));
        });

        server.createContext("/issue", ex -> {
            var qmap = parseQuery(ex.getRequestURI().getQuery()); String token = qmap.get("session");
            if(sessions.get(token)==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            send(ex, issueForm(token));
        });

        server.createContext("/issuebook", ex -> {
            String raw = readAll(ex); var f = parseForm(raw); String token = f.get("session"); String user = sessions.get(token);
            if(user==null){ send(ex, wrapPage("<h2>Access Denied</h2><a href='/'>Login</a>", null)); return; }
            String bidS = f.getOrDefault("bookId",""); int bid=-1;
            try{ bid = Integer.parseInt(bidS.trim()); } catch(Exception e){ send(ex, wrapPage("<h2>Invalid Book ID</h2><a class='link' href='/issue?session="+token+"'>Back</a>", token)); return; }
            Book b = books.get(bid); if(b==null){ send(ex, wrapPage("<h2>Book not found</h2><a class='link' href='/issue?session="+token+"'>Back</a>", token)); return; }
            if(!b.available){ send(ex, wrapPage("<h2>Already issued</h2><a class='link' href='/issue?session="+token+"'>Back</a>", token)); return; }
            b.available=false; issues.put(nextIssueId, new Issue(nextIssueId, bid, user, LocalDate.now())); nextIssueId++; saveBooks(); saveIssues();
            redirect(ex, "/history?session="+token);
        });

        server.createContext("/return", ex -> {
            var q = parseQuery(ex.getRequestURI().getQuery()); String token = q.get("session"); if(sessions.get(token)==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            send(ex, returnForm(token));
        });

        server.createContext("/returnbook", ex -> {
            var q = parseQuery(ex.getRequestURI().getQuery()); String token = q.get("session"); String iidS = q.get("iid");
            String user = sessions.get(token); if(user==null){ send(ex, wrapPage("<h2>Access Denied</h2><a href='/'>Login</a>", null)); return; }
            int iid=-1; try{ iid=Integer.parseInt(iidS);}catch(Exception e){ send(ex, wrapPage("<h2>Invalid Issue ID</h2><a class='link' href='/return?session="+token+"'>Back</a>", token)); return;}
            Issue rec = issues.get(iid); if(rec==null || !rec.username.equals(user)){ send(ex, wrapPage("<h2>Record not found</h2><a class='link' href='/return?session="+token+"'>Back</a>", token)); return; }
            if(rec.returnedOn!=null){ send(ex, wrapPage("<h2>Already returned</h2><a class='link' href='/history?session="+token+"'>History</a>", token)); return; }
            rec.returnedOn = LocalDate.now(); books.get(rec.bookId).available=true; saveBooks(); saveIssues();
            redirect(ex, "/history?session="+token);
        });

        server.createContext("/history", ex -> {
            var q = parseQuery(ex.getRequestURI().getQuery()); String token=q.get("session"); if(sessions.get(token)==null){ send(ex, wrapPage("<h2>Access Denied</h2><a class='link' href='/'>Login</a>", null)); return; }
            send(ex, historyPage(token));
        });

        server.setExecutor(null); server.start();
        System.out.println("SmartLibrary running at http://localhost:8000/");
    }

    // ---------- small utilities ----------
    static void send(HttpExchange ex, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type","text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try(var os = ex.getResponseBody()){ os.write(bytes); }
    }
    static void redirect(HttpExchange ex, String loc) throws IOException {
        ex.getResponseHeaders().add("Location", loc); ex.sendResponseHeaders(302, -1);
    }
    static String readAll(HttpExchange ex) throws IOException { InputStream is = ex.getRequestBody(); return new String(is.readAllBytes(), StandardCharsets.UTF_8); }
    static String escape(String s){ if(s==null) return ""; return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    static String getSessionForUser(String user){ for(var e: sessions.entrySet()) if(e.getValue().equals(user)) return e.getKey(); return null; }
}
