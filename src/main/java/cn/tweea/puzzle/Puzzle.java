/*
 * 版权所有 2024 Tweea。
 * 保留所有权利。
 */
package cn.tweea.puzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class Puzzle {
    static class Board {
        int xLength;

        int yLength;

        int indexLength;

        Board(int xLength, int yLength) {
            this.xLength = xLength;
            this.yLength = yLength;
            this.indexLength = xLength * yLength;
        }

        Situation parseSituation(String postitionString) {
            String[] postitionStringParts = StringUtils.split(postitionString, ',');
            if (postitionStringParts.length != indexLength) {
                throw new IllegalArgumentException("位置个数应为 " + indexLength);
            }

            int[] postitions = new int[indexLength];
            Arrays.fill(postitions, -1);
            for (int i = 0; i < indexLength; i++) {
                int postition = Integer.parseInt(postitionStringParts[i].trim());
                if (postition < 0 || postition >= indexLength) {
                    throw new IllegalArgumentException("位置范围应为 0-" + (indexLength - 1));
                }
                if (ArrayUtils.indexOf(postitions, postition) >= 0) {
                    throw new IllegalArgumentException("位置有重复");
                }

                postitions[i] = postition;
            }
            return new Situation(postitions);
        }

        Situation buildFinalSituation() {
            int[] postitions = new int[indexLength];
            for (int i = 0; i < indexLength; i++) {
                postitions[i] = i;
            }
            return new Situation(postitions);
        }

        List<Situation> traversePath(Situation initialSituation, Situation finalSituation, int maxDepth) {
            Stack<Situation> situationStack = new Stack<>();
            situationStack.push(initialSituation);
            Map<Situation, Integer> situationDepthMap = new HashMap<>();
            situationDepthMap.put(initialSituation, 1);
            return traversePath(initialSituation, finalSituation, situationStack, situationDepthMap, maxDepth);
        }

        List<Situation> traversePath(Situation situation, Situation finalSituation, Stack<Situation> situationStack, Map<Situation, Integer> situationDepthMap,
            int maxDepth) {
            if (situation.equals(finalSituation)) {
                return new ArrayList<>(situationStack);
            }

            int depth = situationStack.size();
            if (depth > maxDepth) {
                return Collections.emptyList();
            }

            int minDepth = situationDepthMap.getOrDefault(situation, Integer.MAX_VALUE);
            if (minDepth < depth) {
                return Collections.emptyList();
            }
            if (minDepth > depth) {
                situationDepthMap.put(situation, depth);
            }

            List<Situation> bestPath = Collections.emptyList();
            for (Situation nextSituation : listNextSituations(situation)) {
                if (situationStack.contains(nextSituation)) {
                    continue;
                }

                situationStack.push(nextSituation);
                List<Situation> path = traversePath(nextSituation, finalSituation, situationStack, situationDepthMap, maxDepth);
                if (!path.isEmpty()) {
                    if (bestPath.isEmpty()) {
                        bestPath = path;
                    } else if (path.size() < bestPath.size()) {
                        bestPath = path;
                    }
                }
                situationStack.pop();
            }
            return bestPath;
        }

        Situation[] listNextSituations(Situation situation) {
            int freeIndex = indexLength - 1;
            int freePostition = situation.postitions[freeIndex];

            int nextSituationNumber = 0;
            if (freePostition % xLength != 0) {
                nextSituationNumber++;
            }
            if (freePostition % xLength != xLength - 1) {
                nextSituationNumber++;
            }
            if (freePostition / xLength != 0) {
                nextSituationNumber++;
            }
            if (freePostition / xLength != yLength - 1) {
                nextSituationNumber++;
            }

            Situation[] nextSituations = new Situation[nextSituationNumber];
            int nextSituationIndex = -1;
            if (freePostition % xLength != 0) {
                nextSituationIndex++;
                nextSituations[nextSituationIndex] = situation.swap(freePostition - 1, freePostition);
            }
            if (freePostition % xLength != xLength - 1) {
                nextSituationIndex++;
                nextSituations[nextSituationIndex] = situation.swap(freePostition + 1, freePostition);
            }
            if (freePostition / xLength != 0) {
                nextSituationIndex++;
                nextSituations[nextSituationIndex] = situation.swap(freePostition - xLength, freePostition);
            }
            if (freePostition / xLength != yLength - 1) {
                nextSituationIndex++;
                nextSituations[nextSituationIndex] = situation.swap(freePostition + xLength, freePostition);
            }
            return nextSituations;
        }

        List<Step> toSteps(List<Situation> path) {
            int pathSize = path.size();
            if (pathSize < 2) {
                throw new IllegalArgumentException("路径步骤数量有错误");
            }

            List<Step> steps = new ArrayList<>(pathSize - 1);
            int freePostition = indexLength - 1;
            for (int i = 1; i < pathSize; i++) {
                int before = path.get(i - 1).postitions[freePostition];
                int after = path.get(i).postitions[freePostition];
                if (after == before - 1) {
                    steps.add(Step.RIGHT);
                } else if (after == before + 1) {
                    steps.add(Step.LEFT);
                } else if (after == before - xLength) {
                    steps.add(Step.DOWN);
                } else if (after == before + xLength) {
                    steps.add(Step.UP);
                } else {
                    throw new IllegalArgumentException("路径步骤有错误");
                }
            }
            return steps;
        }
    }

    static class Situation {
        int[] postitions;

        Situation(int[] postitions) {
            this.postitions = postitions;
        }

        Situation swap(int postition1, int postition2) {
            int index1 = ArrayUtils.indexOf(postitions, postition1);
            int index2 = ArrayUtils.indexOf(postitions, postition2);

            Situation s = new Situation(Arrays.copyOf(postitions, postitions.length));
            s.postitions[index1] = postition2;
            s.postitions[index2] = postition1;
            return s;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(postitions);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Situation)) {
                return false;
            }
            Situation other = (Situation) obj;
            return Arrays.equals(postitions, other.postitions);
        }

        @Override
        public String toString() {
            return "Situation [postitions=" + Arrays.toString(postitions) + "]";
        }
    }

    enum Step {
        RIGHT, LEFT, DOWN, UP;
    }
}
