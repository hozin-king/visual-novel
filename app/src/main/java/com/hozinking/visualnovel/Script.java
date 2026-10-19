package com.hozinking.visualnovel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser untuk format naskah .vn:
 *   label: nama
 *   bg: file.jpg | style: normal|memory|crisis | bgm: track | sfx: suara
 *   Nama[ekspresi]: "dialog..."   |   *narasi...*
 *   choice:
 *     - "opsi" -> label [set: f=1] [if: g>=3]
 *   jump: label | set: f=1 / f+1 | if: f=1 -> a | else -> b
 *   ending: id "Judul"
 * Kondisi [if:] juga bisa menempel di baris dialog/narasi.
 */
public class Script {

    public enum Type { DIALOG, BG, STYLE, BGM, SFX, CHOICE, JUMP, SET, IFJUMP, ENDING }

    public static class Cond {
        String flag, op;
        int value;

        boolean eval(Map<String, Integer> flags) {
            int v = flags.containsKey(flag) ? flags.get(flag) : 0;
            if (op.equals("=")) return v == value;
            if (op.equals("!=")) return v != value;
            if (op.equals(">=")) return v >= value;
            if (op.equals("<=")) return v <= value;
            return false;
        }
    }

    public static class SetOp {
        String flag;
        int mode; // 0=set, 1=incr, 2=decr
        int value;

        void apply(Map<String, Integer> flags) {
            int v = flags.containsKey(flag) ? flags.get(flag) : 0;
            if (mode == 0) flags.put(flag, value);
            else if (mode == 1) flags.put(flag, v + value);
            else flags.put(flag, v - value);
        }
    }

    public static class Option {
        String text, target;
        List<SetOp> sets = new ArrayList<>();
        Cond cond;
    }

    public static class Block {
        Type type;
        String speaker = "";   // DIALOG ("" = narasi)
        String expr = "normal";
        String text = "";      // DIALOG
        Cond cond;             // baris bersyarat
        String arg = "";       // BG/BGM/SFX/JUMP/SET/ENDING-id
        String arg2 = "";      // ENDING judul / IFJUMP else-target
        List<SetOp> sets = new ArrayList<>(); // SET
        String ifOp; int ifVal; // IFJUMP
        List<Option> options = new ArrayList<>(); // CHOICE
    }

    public final List<Block> blocks = new ArrayList<>();
    public final Map<String, Integer> labels = new HashMap<>();

    private static final Pattern COND_P = Pattern.compile("\\[if:\\s*([a-z_]+)\\s*(=|!=|>=|<=)\\s*(\\d+)\\]");
    private static final Pattern SET_P = Pattern.compile("\\[set:\\s*([a-z_]+)\\s*(\\+|-|=)\\s*(\\d+)\\]");

    static Cond parseCond(String s) {
        Matcher m = COND_P.matcher(s);
        if (!m.find()) return null;
        Cond c = new Cond();
        c.flag = m.group(1); c.op = m.group(2); c.value = Integer.parseInt(m.group(3));
        return c;
    }

    static List<SetOp> parseSets(String s) {
        List<SetOp> out = new ArrayList<>();
        Matcher m = SET_P.matcher(s);
        while (m.find()) {
            SetOp op = new SetOp();
            op.flag = m.group(1);
            String o = m.group(2);
            op.value = Integer.parseInt(m.group(3));
            op.mode = o.equals("=") ? 0 : (o.equals("+") ? 1 : 2);
            out.add(op);
        }
        return out;
    }

    /** Set baris biasa: "set: flag=3" / "set: flag+1" */
    static SetOp parseSetLine(String s) {
        s = s.trim();
        SetOp op = new SetOp();
        int pi = s.indexOf('+');
        int mi = s.indexOf('-');
        int ei = s.indexOf('=');
        if (pi > 0) { op.flag = s.substring(0, pi).trim(); op.mode = 1; op.value = Integer.parseInt(s.substring(pi + 1).trim()); }
        else if (mi > 0) { op.flag = s.substring(0, mi).trim(); op.mode = 2; op.value = Integer.parseInt(s.substring(mi + 1).trim()); }
        else { op.flag = s.substring(0, ei).trim(); op.mode = 0; op.value = Integer.parseInt(s.substring(ei + 1).trim()); }
        return op;
    }

    public static Script parse(String src) {
        Script sc = new Script();
        String[] lines = src.split("\n");
        int i = 0;
        while (i < lines.length) {
            String raw = lines[i];
            String t = raw.trim();
            i++;
            if (t.isEmpty() || t.startsWith("#")) continue;

            if (t.startsWith("label:")) {
                sc.labels.put(t.substring(6).trim(), sc.blocks.size());
                continue;
            }
            Block b = new Block();
            if (t.startsWith("bg:")) { b.type = Type.BG; b.arg = t.substring(3).trim(); }
            else if (t.startsWith("style:")) { b.type = Type.STYLE; b.arg = t.substring(6).trim(); }
            else if (t.startsWith("bgm:")) { b.type = Type.BGM; b.arg = t.substring(4).trim(); }
            else if (t.startsWith("sfx:")) { b.type = Type.SFX; b.arg = t.substring(4).trim(); }
            else if (t.startsWith("jump:")) { b.type = Type.JUMP; b.arg = t.substring(5).trim(); }
            else if (t.startsWith("set:")) {
                b.type = Type.SET;
                b.sets.add(parseSetLine(t.substring(4)));
            } else if (t.startsWith("if:")) {
                b.type = Type.IFJUMP;
                String s = t.substring(3).trim();
                // "flag=1 -> a | else -> b"  atau  "flag=1 -> a"
                String[] arr = s.split("->");
                Matcher cm = Pattern.compile("([a-z_]+)\\s*(=|!=|>=|<=)\\s*(\\d+)").matcher(arr[0]);
                if (cm.find()) {
                    b.arg = cm.group(1); b.ifOp = cm.group(2); b.ifVal = Integer.parseInt(cm.group(3));
                }
                String target = "", elseTarget = "";
                if (arr.length >= 2) {
                    String mid = arr[1].trim();
                    int pipe = mid.indexOf('|');
                    target = (pipe >= 0) ? mid.substring(0, pipe).trim() : mid;
                }
                if (arr.length >= 3) elseTarget = arr[2].trim();
                b.text = target;   // target utama (arg dipakai untuk cond flag)
                b.arg2 = elseTarget;
            } else if (t.startsWith("ending:")) {
                b.type = Type.ENDING;
                String s = t.substring(7).trim();
                int sp = s.indexOf(' ');
                b.arg = s.substring(0, sp).trim();
                String title = s.substring(sp).trim();
                if (title.startsWith("\"") && title.endsWith("\""))
                    title = title.substring(1, title.length() - 1);
                b.arg2 = title;
            } else if (t.startsWith("choice:")) {
                b.type = Type.CHOICE;
                while (i < lines.length) {
                    String ol = lines[i].trim();
                    if (!ol.startsWith("- ")) break;
                    i++;
                    Option o = new Option();
                    // - "teks" -> label [set:..] [if:..]
                    Matcher tm = Pattern.compile("-\\s*\"(.*?)\"\\s*->\\s*([a-z_0-9]+)\\s*(.*)").matcher(ol);
                    if (tm.find()) {
                        o.text = tm.group(1);
                        o.target = tm.group(2);
                        String tail = tm.group(3);
                        o.sets = parseSets(tail);
                        o.cond = parseCond(tail);
                    }
                    b.options.add(o);
                }
            } else if (t.startsWith("*")) {
                b.type = Type.DIALOG;
                b.speaker = "";
                String s = t;
                b.cond = parseCond(s);
                if (b.cond != null) s = COND_P.matcher(s).replaceAll("").trim();
                // buang * di ujung/awal
                s = s.trim();
                if (s.startsWith("*")) s = s.substring(1);
                if (s.endsWith("*")) s = s.substring(0, s.length() - 1);
                b.text = s.trim();
            } else {
                // Dialog: Speaker[expr]: "teks" [if:..]
                int qi = t.indexOf(": \"");
                if (qi < 0) continue; // baris tak dikenal -> lewati
                b.type = Type.DIALOG;
                String spk = t.substring(0, qi).trim();
                Matcher em = Pattern.compile("^(.*?)\\[([a-z]+)\\]$").matcher(spk);
                if (em.find()) { b.speaker = em.group(1).trim(); b.expr = em.group(2); }
                else b.speaker = spk;
                String rest = t.substring(qi + 3).trim();
                b.cond = parseCond(rest);
                if (b.cond != null) rest = COND_P.matcher(rest).replaceAll("").trim();
                if (rest.endsWith("\"")) rest = rest.substring(0, rest.length() - 1);
                b.text = rest;
            }
            sc.blocks.add(b);
        }
        return sc;
    }
}
