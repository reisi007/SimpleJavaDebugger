package at.reisisoft.jku.systemsoftwares.debugger;

import java.io.*;

class Redirection extends Thread {
    private Reader in;
    private Writer out;

    Redirection(InputStream is, OutputStream os) {
        super();
        in = new InputStreamReader(is);
        out = new OutputStreamWriter(os);
    }

    public void run() {
        try {
            char[] buf = new char[1024];
            int n;
            while ((n = in.read(buf, 0, 1024)) >= 0)
                out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
        }
    }
}