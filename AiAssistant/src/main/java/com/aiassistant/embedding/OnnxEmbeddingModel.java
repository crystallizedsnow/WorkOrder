package com.aiassistant.embedding;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 DJL + ONNX Runtime 的本地向量化模型实现。
 * <p>
 * 默认加载 BAAI/bge-small-zh-v1.5（中文优化，512 维），通过自定义
 * {@link BgeEmbeddingTranslator} 完成「分词 → 均值池化 → L2 归一化」。
 * <p>
 * 替换原因：DJL 自带的 {@code TextEmbeddingTranslatorFactory} 仅适用于
 * PyTorch 引擎，对 ONNX 版 BGE 模型输入名称不匹配，会报
 * "No model matching the criteria is found"。
 * <p>
 * 模型目录需包含 tokenizer.json 与 onnx/model.onnx。
 * 加载失败时 isAvailable() 返回 false，RAG 自动降级为不检索。
 * <p>
 * 路径解析：支持绝对路径与相对路径。相对路径会依次尝试基于
 * 当前工作目录、user.dir 解析，避免不同启动方式下找不到 tokenizer.json。
 */
@Slf4j
public class OnnxEmbeddingModel implements EmbeddingModel {

    private final ZooModel<String, float[]> model;
    private final int dimension;
    private final boolean available;

    public OnnxEmbeddingModel(String modelPath, String modelName, int dimension) {
        this.dimension = dimension;
        ZooModel<String, float[]> loaded = null;
        boolean ok = false;
        // 解析为实际可用的绝对路径（兼容不同工作目录启动场景）
        Path resolvedPath = resolveModelPath(modelPath);
        try {
            // 1. 构造 HuggingFace 分词器（指定路径下的 tokenizer.json）
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(resolvedPath)
                    .optMaxLength(512)
                    .optTruncation(true)
                    .optPadding(true)
                    .build();

            // 2. 自定义 Translator：分词 + 均值池化 + L2 归一化
            BgeEmbeddingTranslator translator = new BgeEmbeddingTranslator(tokenizer, true);

            // 3. 加载 ONNX 模型
            Criteria.Builder<String, float[]> builder = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(resolvedPath)
                    .optEngine("OnnxRuntime")
                    .optTranslator(translator);

            if (modelName != null && !modelName.isBlank()) {
                builder.optModelName(modelName);
            }

            loaded = builder.build().loadModel();
            ok = true;
            log.info("ONNX 向量化模型加载成功: path={} (resolved={}), name={}, dim={}",
                    modelPath, resolvedPath, modelName, dimension);
        } catch (Exception e) {
            log.warn("ONNX 向量化模型加载失败，RAG 将降级为不检索: path={}, resolved={}, 原因={}",
                    modelPath, resolvedPath, e.getMessage());
        }
        this.model = loaded;
        this.available = ok;
    }

    /**
     * 解析模型路径为实际可用的绝对路径。
     * <p>
     * 依次尝试：
     * <ol>
     *   <li>原路径（可能是绝对路径或相对于当前工作目录的路径）</li>
     *   <li>user.dir + 原路径（Spring Boot 启动目录）</li>
     *   <li>基于 classpath 推断的项目根目录 + 原路径</li>
     * </ol>
     * 返回第一个存在 tokenizer.json 的目录路径；都不存在则返回原路径（让后续加载报错）。
     */
    private static Path resolveModelPath(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return Paths.get(modelPath);
        }
        Path raw = Paths.get(modelPath);
        // 绝对路径直接返回
        if (raw.isAbsolute()) {
            return raw;
        }
        // 候选基准目录
        List<Path> candidates = new ArrayList<>();
        // 1. 当前工作目录
        candidates.add(raw);
        // 2. user.dir（Java 进程工作目录）
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            candidates.add(Paths.get(userDir).resolve(raw));
        }
        // 3. 基于 classpath 推断项目根目录（target/classes 的上一级）
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            String[] parts = classpath.split(File.pathSeparator);
            for (String part : parts) {
                if (part.endsWith("target" + File.separator + "classes") || part.endsWith("target/classes")) {
                    Path targetClasses = Paths.get(part).toAbsolutePath();
                    Path projectRoot = targetClasses.getParent().getParent();
                    candidates.add(projectRoot.resolve(raw));
                    break;
                }
            }
        }
        // 返回第一个存在 tokenizer.json 的候选路径
        for (Path candidate : candidates) {
            if (candidate.resolve("tokenizer.json").toFile().exists()) {
                return candidate.toAbsolutePath();
            }
        }
        // 都不存在，返回 user.dir + raw 作为默认（让加载阶段报具体错误）
        if (userDir != null) {
            return Paths.get(userDir).resolve(raw).toAbsolutePath();
        }
        return raw;
    }

    @Override
    public float[] embed(String text) {
        if (!available || text == null || text.isEmpty()) {
            return new float[0];
        }
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return predictor.predict(text);
        } catch (Exception e) {
            log.warn("向量化失败: {}", e.getMessage());
            return new float[0];
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        if (!available || texts == null) {
            return results;
        }
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            for (String text : texts) {
                if (text == null || text.isEmpty()) {
                    results.add(new float[0]);
                } else {
                    results.add(predictor.predict(text));
                }
            }
        } catch (Exception e) {
            log.warn("批量向量化失败，回退逐条处理: {}", e.getMessage());
            for (String text : texts) {
                results.add(embed(text));
            }
        }
        return results;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 释放模型资源（容器销毁时调用）。
     */
    public void close() {
        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
                log.warn("关闭 ONNX 模型失败: {}", e.getMessage());
            }
        }
    }
}
