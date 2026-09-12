package com.example.rootrunner;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;?]*[A-Za-z]");
    private static final String PREFS = "root_runner";
    private static final String KEY_SCRIPTS = "scripts";
    private static final char FIELD_SEP = '\u0001';
    private static final char LINE_SEP = '\n';

    private static final int TERM_EXPANDED_HEIGHT = 340;
    private static final int TERM_HEAD_HEIGHT = 32;
    private static final int TERM_COLLAPSED_HEIGHT = 90;

    private LinearLayout scriptList;
    private TextView output;
    private ScrollView outputScroll;
    private EditText inputBox;
    private Button sendBtn;

    private LinearLayout term;
    private LinearLayout inputRow;
    private TextView termArrow;
    private boolean termExpanded = true;

    private Process currentProcess;
    private OutputStream processStdin;
    private Dialog rootDialog;

    private final List<Script> scripts = new ArrayList<>();

    private static class Script {
        String name;
        String path;
        Script(String name, String path) { this.name = name; this.path = path; }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF0F4F8);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Root 脚本启动器");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF0F1A2B);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("ROOT SCRIPT LAUNCHER");
        subtitle.setTextSize(10);
        subtitle.setTextColor(0xFF546A82);
        subtitle.setLetterSpacing(0.12f);
        titleBox.addView(subtitle);

        TextView author = new TextView(this);
        author.setText("陈延辉大丑逼 制作");
        author.setTextSize(10);
        author.setTextColor(0xFF7D8FA4);
        titleBox.addView(author);

        Button addBtn = new Button(this);
        addBtn.setText("＋ 添加");
        addBtn.setTextSize(13);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackground(roundRect(0xFF0284C7, 20));
        addBtn.setPadding(dp(16), 0, dp(16), 0);
        addBtn.setMinWidth(0);
        addBtn.setMinimumWidth(0);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickScript(); }
        });
        top.addView(addBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setPadding(dp(12), 0, dp(12), dp(12));
        root.addView(listScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scriptList = new LinearLayout(this);
        scriptList.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(scriptList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        term = new LinearLayout(this);
        term.setOrientation(LinearLayout.VERTICAL);
        term.setBackgroundColor(0xFF0A0E1A);
        root.addView(term, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TERM_EXPANDED_HEIGHT)));

        LinearLayout termHead = new LinearLayout(this);
        termHead.setOrientation(LinearLayout.HORIZONTAL);
        termHead.setGravity(Gravity.CENTER_VERTICAL);
        termHead.setPadding(dp(12), 0, dp(12), 0);
        termHead.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTerminal(); }
        });
        term.addView(termHead, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TERM_HEAD_HEIGHT)));

        TextView leftSpacer = new TextView(this);
        leftSpacer.setText("      ");
        leftSpacer.setTextSize(11);
        termHead.addView(leftSpacer);

        termArrow = new TextView(this);
        termArrow.setText("▼");
        termArrow.setTextSize(11);
        termArrow.setTextColor(0xFF475569);
        termArrow.setGravity(Gravity.CENTER);
        termHead.addView(termArrow, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearBtn = new TextView(this);
        clearBtn.setText("清除");
        clearBtn.setTextSize(11);
        clearBtn.setTextColor(0xFF64748B);
        clearBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                output.setText("");
                appendOutput("已清空", 3);
            }
        });
        termHead.addView(clearBtn);

        outputScroll = new ScrollView(this);
        LinearLayout.LayoutParams osLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        osLp.leftMargin = dp(12);
        osLp.rightMargin = dp(12);
        term.addView(outputScroll, osLp);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(11);
        output.setTextColor(0xFFA7F3D0);
        output.setMovementMethod(new ScrollingMovementMethod());
        outputScroll.addView(output);

        inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(12), dp(6), dp(12), dp(10));
        term.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView prompt = new TextView(this);
        prompt.setText("$");
        prompt.setTextColor(0xFF7DD3FC);
        prompt.setTextSize(14);
        prompt.setTypeface(Typeface.MONOSPACE);
        inputRow.addView(prompt);

        inputBox = new EditText(this);
        inputBox.setHint("输入命令 / 选项后回车");
        inputBox.setHintTextColor(0xFF475569);
        inputBox.setTextColor(0xFFCBD5E1);
        inputBox.setTextSize(12);
        inputBox.setTypeface(Typeface.MONOSPACE);
        inputBox.setBackgroundColor(0xFF0F172A);
        inputBox.setPadding(dp(8), dp(8), dp(8), dp(8));
        inputBox.setSingleLine(true);
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ibLp.leftMargin = dp(8);
        ibLp.rightMargin = dp(8);
        inputRow.addView(inputBox, ibLp);

        sendBtn = new Button(this);
        sendBtn.setText("发送");
        sendBtn.setTextSize(12);
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setBackground(roundRect(0xFF0284C7, 14));
        sendBtn.setMinWidth(0);
        sendBtn.setMinimumWidth(0);
        sendBtn.setPadding(dp(14), 0, dp(14), 0);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendInput(); }
        });
        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        inputBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                sendInput();
                return true;
            }
        });

        setContentView(root);

        appendOutput("请点击「＋ 添加」选择脚本", 3);
        checkRootOnStart();
        loadScripts();
    }

    // ========== 启动时检查 root ==========
    private void checkRootOnStart() {
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line = r.readLine();
                    if (line != null && line.contains("uid=0")) ok = true;
                    p.waitFor();
                } catch (Exception ignored) {}

                final boolean success = ok;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (success) {
                            appendOutput("✓ 已获取 root 权限", 2);
                        } else {
                            showRootDialog();
                        }
                    }
                });
            }
        }).start();
    }

    // ========== "需要 root 权限" 简单弹窗 ==========
    private void showRootDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(26), dp(24), dp(20));
        box.setBackground(roundRect(0xFFFFFFFF, 22));

        TextView icon = new TextView(this);
        icon.setText("🔑");
        icon.setTextSize(46);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);

        TextView title = new TextView(this);
        title.setText("需要 root 权限");
        title.setTextSize(19);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF0F1A2B);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(12);
        box.addView(title, tLp);

        TextView desc = new TextView(this);
        desc.setText("本软件需要 root 权限才能执行脚本。\n\n请打开你的 Root 管理器\n（KernelSU / Magisk）\n在超级用户列表里允许本应用，\n然后回到这里点「重试」。");
        desc.setTextSize(13);
        desc.setTextColor(0xFF546A82);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(2), 1);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(14);
        box.addView(desc, dLp);

        Button retryBtn = new Button(this);
        retryBtn.setText("重试");
        retryBtn.setTextSize(14);
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setBackground(roundRect(0xFF0284C7, 14));
        retryBtn.setMinWidth(0);
        retryBtn.setMinimumWidth(0);
        retryBtn.setAllCaps(false);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rLp.topMargin = dp(24);
        box.addView(retryBtn, rLp);

        Button exitBtn = new Button(this);
        exitBtn.setText("退出");
        exitBtn.setTextSize(13);
        exitBtn.setTextColor(0xFF64748B);
        exitBtn.setBackground(roundRect(0xFFF0F4F8, 14));
        exitBtn.setMinWidth(0);
        exitBtn.setMinimumWidth(0);
        exitBtn.setAllCaps(false);
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        eLp.topMargin = dp(8);
        box.addView(exitBtn, eLp);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(28), 0, dp(28), 0);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootDialog = new Dialog(this);
        rootDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        rootDialog.setContentView(wrapper);
        rootDialog.setCancelable(false);
        if (rootDialog.getWindow() != null) {
            rootDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            rootDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        retryBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                retryBtn.setText("检测中…");
                retryBtn.setEnabled(false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        boolean ok = false;
                        try {
                            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                            String line = r.readLine();
                            if (line != null && line.contains("uid=0")) ok = true;
                            p.waitFor();
                        } catch (Exception ignored) {}
                        final boolean success = ok;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (success) {
                                    appendOutput("✓ 已获取 root 权限", 2);
                                    if (rootDialog != null && rootDialog.isShowing())
                                        rootDialog.dismiss();
                                } else {
                                    appendOutput("⚠ 仍未授权，请到 KernelSU / Magisk 里允许本应用", 1);
                                    retryBtn.setText("重试");
                                    retryBtn.setEnabled(true);
                                }
                            }
                        });
                    }
                }).start();
            }
        });

        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (rootDialog != null && rootDialog.isShowing()) rootDialog.dismiss();
                // 真正退出 App
                finishAffinity();
                System.exit(0);
            }
        });

        rootDialog.show();
    }

    private void toggleTerminal() {
        termExpanded = !termExpanded;
        ViewGroup.LayoutParams lp = term.getLayoutParams();
        lp.height = dp(termExpanded ? TERM_EXPANDED_HEIGHT : TERM_COLLAPSED_HEIGHT);
        term.setLayoutParams(lp);
        outputScroll.setVisibility(termExpanded ? View.VISIBLE : View.GONE);
        inputRow.setVisibility(View.VISIBLE);
        termArrow.setText(termExpanded ? "▼" : "▲");
    }

    private void saveScripts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scripts.size(); i++) {
            if (i > 0) sb.append(LINE_SEP);
            Script s = scripts.get(i);
            sb.append(s.name).append(FIELD_SEP).append(s.path);
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.edit().putString(KEY_SCRIPTS, sb.toString()).apply();
    }

    private void loadScripts() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String data = sp.getString(KEY_SCRIPTS, "");
        scripts.clear();
        scriptList.removeAllViews();
        if (data.isEmpty()) { showEmptyHint(); return; }

        String[] lines = data.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int idx = line.indexOf(FIELD_SEP);
            if (idx <= 0) continue;
            String name = line.substring(0, idx);
            String path = line.substring(idx + 1);
            File f = new File(path);
            if (!f.exists()) continue;
            Script s = new Script(name, path);
            scripts.add(s);
            addCard(s);
        }
        if (scripts.isEmpty()) showEmptyHint();
        else appendOutput("✓ 已恢复 " + scripts.size() + " 个脚本", 2);
    }

    private void showEmptyHint() {
        TextView empty = new TextView(this);
        empty.setText("还没有脚本\n点击右上角「＋ 添加」选择文件");
        empty.setTextSize(13);
        empty.setTextColor(0xFF7D8FA4);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(60), dp(20), dp(60));
        empty.setTag("empty");
        scriptList.addView(empty);
    }

    private void pickScript() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        startActivityForResult(Intent.createChooser(i, "选择脚本"), REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) importScript(uri);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        if (result == null) result = "script_" + System.currentTimeMillis() + ".sh";
        return result;
    }

    private boolean scriptExists(String name) {
        for (Script s : scripts) {
            if (s.name.equals(name)) return true;
        }
        return false;
    }

    private void importScript(final Uri uri) {
        final String displayName = getFileName(uri);
        if (scriptExists(displayName)) {
            appendOutput("⚠ 已存在同名脚本，跳过: " + displayName, 1);
            return;
        }

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String safeName = displayName.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
                    final File dst = new File(getFilesDir(), System.currentTimeMillis() + "_" + safeName);

                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("无法读取文件");

                    FileOutputStream out = new FileOutputStream(dst);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    out.close();

                    try {
                        Runtime.getRuntime().exec(new String[]{"su", "-c",
                                "chmod 755 '" + dst.getAbsolutePath() + "'"}).waitFor();
                    } catch (Exception ignored) {}

                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (scriptExists(displayName)) {
                                try { dst.delete(); } catch (Exception ignored) {}
                                appendOutput("⚠ 已存在同名脚本，跳过: " + displayName, 1);
                                return;
                            }
                            Script s = new Script(displayName, dst.getAbsolutePath());
                            scripts.add(s);
                            addCard(s);
                            saveScripts();
                            appendOutput("✓ 已导入: " + displayName, 2);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            appendOutput("[导入失败] " + e.getMessage(), 1);
                        }
                    });
                }
            }
        }).start();
    }

    private void addCard(final Script s) {
        View emptyView = scriptList.findViewWithTag("empty");
        if (emptyView != null) scriptList.removeView(emptyView);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(roundRect(0xFFFFFFFF, 14));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.bottomMargin = dp(8);
        card.setLayoutParams(cLp);

        TextView tag = new TextView(this);
        tag.setText("SH");
        tag.setTextSize(11);
        tag.setTypeface(null, Typeface.BOLD);
        tag.setTextColor(Color.WHITE);
        tag.setGravity(Gravity.CENTER);
        tag.setBackground(roundRect(0xFF7EC8E3, 10));
        card.addView(tag, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView nameView = new TextView(this);
        nameView.setText(s.name);
        nameView.setTextSize(14);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setTextColor(0xFF0F1A2B);
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nLp.leftMargin = dp(12);
        nLp.rightMargin = dp(8);
        card.addView(nameView, nLp);

        Button delBtn = new Button(this);
        delBtn.setText("✕");
        delBtn.setTextSize(12);
        delBtn.setTextColor(0xFF546A82);
        delBtn.setBackground(roundRect(0xFFE2E8F0, 10));
        delBtn.setMinWidth(0);
        delBtn.setMinimumWidth(0);
        delBtn.setPadding(0, 0, 0, 0);
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { new File(s.path).delete(); } catch (Exception ignored) {}
                scripts.remove(s);
                scriptList.removeView(card);
                saveScripts();
                if (scripts.isEmpty()) showEmptyHint();
            }
        });
        card.addView(delBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        Button runBtn = new Button(this);
        runBtn.setText("▶");
        runBtn.setTextSize(13);
        runBtn.setTextColor(Color.WHITE);
        runBtn.setBackground(roundRect(0xFF0284C7, 10));
        runBtn.setMinWidth(0);
        runBtn.setMinimumWidth(0);
        runBtn.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        rLp.leftMargin = dp(6);
        runBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runScript(s); }
        });
        card.addView(runBtn, rLp);

        scriptList.addView(card);
    }

    private void runScript(final Script s) {
        killCurrentProcess();
        if (!termExpanded) toggleTerminal();

        appendOutput("$ " + s.name, 4);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String cmd = "chmod 755 '" + s.path + "' && " +
                            "script -q -c \"'" + s.path + "'\" /dev/null";

                    ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
                    pb.redirectErrorStream(true);
                    currentProcess = pb.start();
                    processStdin = currentProcess.getOutputStream();

                    InputStream is = currentProcess.getInputStream();
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    long lastDataTime = System.currentTimeMillis();

                    while (currentProcess.isAlive() || is.available() > 0) {
                        if (is.available() > 0) {
                            int b = is.read();
                            if (b == -1) break;
                            buf.write(b);
                            lastDataTime = System.currentTimeMillis();

                            if (b == '\n' || b == '\r') {
                                flushLine(buf);
                            }
                        } else {
                            long now = System.currentTimeMillis();
                            if (buf.size() > 0 && now - lastDataTime >= 150) {
                                flushPartial(buf);
                                lastDataTime = now;
                            }
                            Thread.sleep(15);
                        }
                    }

                    if (buf.size() > 0) flushLine(buf);

                    int code = currentProcess.waitFor();
                    final int codeFinal = code;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (codeFinal == 0) appendOutput("✓ 执行完成 (退出码 0)", 2);
                            else appendOutput("✗ 退出码: " + codeFinal, 1);
                            currentProcess = null;
                            processStdin = null;
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            appendOutput("[错误] " + e.getMessage(), 1);
                            currentProcess = null;
                            processStdin = null;
                        }
                    });
                }
            }
        }).start();
    }

    private void flushLine(ByteArrayOutputStream buf) {
        if (buf.size() == 0) return;
        byte[] data = buf.toByteArray();
        buf.reset();
        String line = stripAnsi(decodeBytesSmart(data));
        if (line.length() == 0) return;
        final String lineFinal = line;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                appendRaw(lineFinal + "\n", 2);
            }
        });
    }

    private void flushPartial(ByteArrayOutputStream buf) {
        if (buf.size() == 0) return;
        byte[] data = buf.toByteArray();

        int keep = data.length > 8 ? 3 : 0;
        int showLen = data.length - keep;
        if (showLen <= 0) return;

        byte[] show = new byte[showLen];
        System.arraycopy(data, 0, show, 0, showLen);
        byte[] keepBytes = new byte[keep];
        System.arraycopy(data, showLen, keepBytes, 0, keep);

        buf.reset();
        if (keep > 0) buf.write(keepBytes, 0, keep);

        String text = stripAnsi(decodeBytesSmart(show));
        if (text.length() == 0) return;
        final String textFinal = text;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                appendRaw(textFinal, 2);
            }
        });
    }

    private String decodeBytesSmart(byte[] data) {
        try {
            java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (Exception ignored) {
            try {
                return new String(data, Charset.forName("GBK"));
            } catch (Exception e) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
    }

    private void killCurrentProcess() {
        if (currentProcess != null) {
            try { currentProcess.destroy(); } catch (Exception ignored) {}
            currentProcess = null;
            processStdin = null;
        }
    }

    private void sendInput() {
        String text = inputBox.getText().toString();
        inputBox.setText("");

        if (processStdin == null) {
            appendOutput("[提示] 没有正在运行的脚本", 1);
            return;
        }

        appendOutput("> " + text, 4);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    processStdin.write((text + "\n").getBytes("UTF-8"));
                    processStdin.flush();
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            appendOutput("[输入失败] " + e.getMessage(), 1);
                        }
                    });
                }
            }
        }).start();
    }

    private String stripAnsi(String s) {
        return ANSI.matcher(s).replaceAll("");
    }

    private void appendRaw(String text, int type) {
        int color = colorForType(type);
        SpannableString span = new SpannableString(text);
        span.setSpan(new ForegroundColorSpan(color), 0, span.length(), 0);
        output.append(span);
        outputScroll.post(new Runnable() {
            @Override public void run() {
                outputScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void appendOutput(String text, int type) {
        appendRaw(text + "\n", type);
    }

    private int colorForType(int type) {
        switch (type) {
            case 1: return 0xFFFB7185;
            case 2: return 0xFF4ADE80;
            case 3: return 0xFFCBD5E1;
            case 4: return 0xFF7DD3FC;
            default: return 0xFF94A3B8;
        }
    }
            }
