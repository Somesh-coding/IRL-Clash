package com.irlclash;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MissionFactory {

    record T(String c, String t, String i, int s) {}

    final List<T> all = List.of(
            new T("LEAF", "Leaf Hunt",
                    "Find an interesting outdoor leaf and make it the clear subject.", 120),

            new T("TREE", "Tree Spot",
                    "Capture an interesting tree with unusual shape, texture or light.", 120),

            new T("STATUE", "Statue Search",
                    "Find a safe public statue, sculpture or monument.", 120),

            new T("CYCLE", "Cycle Hunt",
                    "Find a bicycle or bicycle wheel in a safe public setting.", 120),

            new T("SAND", "Sand Scene",
                    "Capture interesting sand, gravel or sandy outdoor texture.", 120),

            new T("WATER", "Water Watch",
                    "Capture a safe outdoor water scene such as a fountain, puddle, stream or pond.", 120),

            new T("FLOWER", "Flower Power",
                    "Find an outdoor flower and make it the clear subject.", 120),

            new T("ROCK", "Rock Hunt",
                    "Find an interesting outdoor rock or stone formation.", 120),

            new T("SHADOW", "Shadow Shot",
                    "Capture an interesting natural or object shadow outdoors.", 120),

            new T("BENCH", "Bench Check",
                    "Find a public outdoor bench and photograph it safely.", 120),

            new T("TEXTURE", "Texture Hunt",
                    "Capture an interesting safe outdoor texture such as bark, stone or grass.", 120),

            new T("CLOUD", "Cloud Catch",
                    "Capture an interesting cloud formation from a safe place.", 120),

            new T("BUILDING", "Architecture Detail",
                    "Capture an interesting safe exterior detail of a building or public structure.", 120)
    );

    public List<Map<String, Object>> pick() {

        List<T> x = new ArrayList<>(all);
        Collections.shuffle(x);

        List<Map<String, Object>> r = new ArrayList<>();

        for (int n = 0; n < 5; n++) {
            T t = x.get(n);

            r.add(Map.of(
                    "round", n + 1,
                    "category", t.c,
                    "title", t.t,
                    "instruction", t.i,
                    "seconds", t.s
            ));
        }

        return r;
    }
}