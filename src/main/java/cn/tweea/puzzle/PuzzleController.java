/*
 * 版权所有 2024 Tweea。
 * 保留所有权利。
 */
package cn.tweea.puzzle;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import cn.tweea.puzzle.Puzzle.Board;
import cn.tweea.puzzle.Puzzle.Situation;
import cn.tweea.puzzle.Puzzle.Step;

@RestController
@RequestMapping("/api")
public class PuzzleController {
    private static final Logger LOG = LoggerFactory.getLogger(PuzzleController.class);

    @ExceptionHandler(value = Throwable.class)
    @ResponseBody
    public ResultDto handleException(Throwable t) {
        LOG.error("转换异常消息", t);

        return new ResultDto(1, "操作失败：" + t.getLocalizedMessage(), null);
    }

    @GetMapping(value = "/puzzle")
    public ResultDto puzzle(HttpServletRequest request) {
        String initialSituationString = request.getParameter("initialSituationString");
        int maxDepth = Integer.parseInt(request.getParameter("maxDepth"));
        if (maxDepth < 1) {
            maxDepth = 1;
        } else if (maxDepth > 30) {
            maxDepth = 30;
        }

        Board board = new Board(3, 3);
        Situation initialSituation = board.parseSituation(initialSituationString);
        Situation finalSituation = board.buildFinalSituation();
        List<Situation> bestPath = board.traversePath(initialSituation, finalSituation, maxDepth);
        List<String> stepStrings = new ArrayList<>();
        if (bestPath.isEmpty()) {
            stepStrings.add("没有找到");
        } else if (bestPath.size() == 1) {
            stepStrings.add("无需移动");
        } else {
            List<Step> steps = board.toSteps(bestPath);
            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                switch (step) {
                case RIGHT:
                    stepStrings.add((i + 1) + ": 右");
                    break;
                case LEFT:
                    stepStrings.add((i + 1) + ": 左");
                    break;
                case DOWN:
                    stepStrings.add((i + 1) + ": 下");
                    break;
                case UP:
                    stepStrings.add((i + 1) + ": 上");
                    break;
                default:
                    throw new IllegalArgumentException("步骤有错误");
                }
            }
        }
        return new ResultDto(0, null, stepStrings);
    }
}
