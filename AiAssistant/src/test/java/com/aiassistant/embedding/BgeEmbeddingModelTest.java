package com.aiassistant.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 BGE-small-zh-v1.5 ONNX 模型能正常加载并产出向量。
 * <p>
 * 不依赖 Spring 上下文，直接构造 {@link OnnxEmbeddingModel}。
 */
@DisplayName("BGE-small-zh-v1.5 ONNX 模型加载与向量化测试")
class BgeEmbeddingModelTest {

    @Test
    @DisplayName("模型加载成功且维度为 512")
    void testModelLoadAndDimension() {
        OnnxEmbeddingModel model = new OnnxEmbeddingModel(
                "models/bge-small-zh-v1.5", "onnx/model", 512);
        assertTrue(model.isAvailable(), "BGE 模型应加载成功");
        assertEquals(512, model.dimension(), "BGE-small-zh-v1.5 维度应为 512");
        model.close();
    }

    @Test
    @DisplayName("中文文本向量化产出非零 512 维向量")
    void testChineseEmbedding() {
        OnnxEmbeddingModel model = new OnnxEmbeddingModel(
                "models/bge-small-zh-v1.5", "onnx/model", 512);
        if (!model.isAvailable()) {
            model.close();
            fail("模型未加载成功，无法执行向量化测试");
        }
        float[] vec = model.embed("工单系统状态查询");
        assertEquals(512, vec.length, "向量维度应为 512");
        float norm = 0;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        assertTrue(norm > 0.1f, "向量 L2 范数应大于 0.1（已归一化应接近 1）");
        model.close();
    }

    @Test
    @DisplayName("相似中文文本向量余弦相似度高于不相关文本")
    void testSemanticSimilarity() {
        OnnxEmbeddingModel model = new OnnxEmbeddingModel(
                "models/bge-small-zh-v1.5", "onnx/model", 512);
        if (!model.isAvailable()) {
            model.close();
            fail("模型未加载成功");
        }
        float[] q = model.embed("查询工单状态");
        float[] sim = model.embed("如何查看工单当前状态");
        float[] diff = model.embed("今天天气真好适合出去玩");

        double cosQS = cosine(q, sim);
        double cosQD = cosine(q, diff);
        System.out.println("cos(q, sim) = " + cosQS);
        System.out.println("cos(q, diff) = " + cosQD);
        assertTrue(cosQS > cosQD, "相似文本的余弦相似度应高于不相关文本");
        model.close();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }
}
