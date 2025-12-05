-- ============================================
-- 初始化 PostgreSQL 扩展
-- ============================================
CREATE EXTENSION IF NOT EXISTS vector;      -- 向量存储扩展，用于AI向量检索
CREATE EXTENSION IF NOT EXISTS hstore;      -- 键值对存储扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- UUID生成扩展

-- ============================================
-- 向量存储表 (vector_store) - 用于RAG知识库向量存储
-- ============================================
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,  -- 主键ID (UUID)
    content text,                                     -- 文本内容
    metadata json,                                    -- 元数据 (JSON格式)
    embedding vector(1536)                            -- 向量嵌入 (1536维)
);
COMMENT ON TABLE vector_store IS '向量存储表，用于RAG知识库的向量检索';
COMMENT ON COLUMN vector_store.id IS '主键ID (UUID)';
COMMENT ON COLUMN vector_store.content IS '文本内容';
COMMENT ON COLUMN vector_store.metadata IS '元数据信息 (JSON格式)';
COMMENT ON COLUMN vector_store.embedding IS '向量嵌入 (1536维，用于相似度检索)';

-- 创建HNSW索引，用于向量余弦相似度检索
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);


-- ============================================
-- 用户表 (tb_user) 表结构
-- ============================================
DROP TABLE IF EXISTS "public"."tb_user";
CREATE TABLE "public"."tb_user" (
                                    "id" "pg_catalog"."int4" NOT NULL,
                                    "name" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "user_name" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "password" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "phone" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "sex" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "id_number" "pg_catalog"."varchar" COLLATE "pg_catalog"."default" NOT NULL,
                                    "status" "pg_catalog"."int4" NOT NULL DEFAULT 1,
                                    "create_time" "pg_catalog"."date",
                                    "update_time" "pg_catalog"."date",
                                    "create_user" "pg_catalog"."int8",
                                    "update_user" "pg_catalog"."int8"
)
;
COMMENT ON TABLE "public"."tb_user" IS '用户信息表，存储系统用户基本信息';
COMMENT ON COLUMN "public"."tb_user"."id" IS '主键';
COMMENT ON COLUMN "public"."tb_user"."name" IS '姓名';
COMMENT ON COLUMN "public"."tb_user"."user_name" IS '用户名';
COMMENT ON COLUMN "public"."tb_user"."password" IS '密码';
COMMENT ON COLUMN "public"."tb_user"."phone" IS '手机号';
COMMENT ON COLUMN "public"."tb_user"."sex" IS '性别';
COMMENT ON COLUMN "public"."tb_user"."id_number" IS '身份证号';
COMMENT ON COLUMN "public"."tb_user"."status" IS '状态 0：禁用 1：启用';
COMMENT ON COLUMN "public"."tb_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."tb_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."tb_user"."create_user" IS '创建人';
COMMENT ON COLUMN "public"."tb_user"."update_user" IS '修改人';

-- ----------------------------
-- tb_user 表初始数据
-- ----------------------------
INSERT INTO "public"."tb_user" VALUES (666497, '管理员', 'admin', '21232f297a57a5a743894a0e4a801fc3', '13800138000', '男', '11010519491231002X', 1, '2025-03-03', '2025-03-03', NULL, NULL);

-- ----------------------------
-- tb_user 表主键约束
-- ----------------------------
ALTER TABLE "public"."tb_user" ADD CONSTRAINT "user_pkey" PRIMARY KEY ("id");



-- ============================================
-- 阿里云OSS文件表 (ali_oss_file) 表结构
-- ============================================
DROP TABLE IF EXISTS "public"."ali_oss_file";
CREATE TABLE "public"."ali_oss_file" (
                                         "id" "pg_catalog"."int8" NOT NULL,
                                         "file_name" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                         "url" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                         "vector_id" "pg_catalog"."text" COLLATE "pg_catalog"."default",
                                         "create_time" "pg_catalog"."timestamp",
                                         "update_time" "pg_catalog"."timestamp"
)
;
COMMENT ON TABLE "public"."ali_oss_file" IS '阿里云OSS文件表，存储上传文件信息及其向量ID';
COMMENT ON COLUMN "public"."ali_oss_file"."id" IS '主键id';
COMMENT ON COLUMN "public"."ali_oss_file"."file_name" IS '文件名';
COMMENT ON COLUMN "public"."ali_oss_file"."url" IS '链接地址';
COMMENT ON COLUMN "public"."ali_oss_file"."vector_id" IS '该文件分割出的多段向量文本ID';
COMMENT ON COLUMN "public"."ali_oss_file"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."ali_oss_file"."update_time" IS '更新时间';

-- ----------------------------
-- ali_oss_file 表主键约束
-- ----------------------------
ALTER TABLE "public"."ali_oss_file" ADD CONSTRAINT "ali_oss_file_pkey" PRIMARY KEY ("id");





-- ============================================
-- 会话表 (sessions) - 用于存储对话会话信息
-- ============================================
DROP TABLE IF EXISTS "public"."chat_session";
CREATE TABLE "public"."chat_session" (
    "id" uuid DEFAULT uuid_generate_v4() NOT NULL,          -- 会话ID (UUID主键)
    "user_id" "pg_catalog"."int8",                          -- 用户标识
    "title" "pg_catalog"."varchar"(255) COLLATE "pg_catalog"."default", -- 会话标题
    "created_at" "pg_catalog"."timestamp" DEFAULT CURRENT_TIMESTAMP,    -- 创建时间
    "updated_at" "pg_catalog"."timestamp" DEFAULT CURRENT_TIMESTAMP     -- 更新时间
);
COMMENT ON TABLE "public"."chat_session" IS '对话会话表';
COMMENT ON COLUMN "public"."chat_session"."id" IS '会话ID (UUID)';
COMMENT ON COLUMN "public"."chat_session"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."chat_session"."title" IS '会话标题';
COMMENT ON COLUMN "public"."chat_session"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."chat_session"."updated_at" IS '更新时间';

ALTER TABLE "public"."chat_session" ADD CONSTRAINT "chat_session_pkey" PRIMARY KEY ("id");

-- chat_session 表索引：用户ID索引，便于按用户查询会话列表
CREATE INDEX idx_chat_session_user_id ON "public"."chat_session" ("user_id");


-- ============================================
-- 消息表 (messages) - 用于存储对话消息记录
-- ============================================
DROP TABLE IF EXISTS "public"."chat_message";
CREATE TABLE "public"."chat_message" (
    "id" "pg_catalog"."int8" NOT NULL,                      -- 消息ID (自增主键)
    "session_id" uuid NOT NULL,                              -- 会话ID (外键)
    "role" "pg_catalog"."varchar"(20) COLLATE "pg_catalog"."default" NOT NULL, -- 角色: user/assistant/system
    "content" "pg_catalog"."text" COLLATE "pg_catalog"."default", -- 消息内容
    "embedding" vector(1536),                                -- 向量嵌入 (预留字段，暂不使用)
    "created_at" "pg_catalog"."timestamp" DEFAULT CURRENT_TIMESTAMP -- 创建时间
);
COMMENT ON TABLE "public"."chat_message" IS '对话消息表';
COMMENT ON COLUMN "public"."chat_message"."id" IS '消息ID';
COMMENT ON COLUMN "public"."chat_message"."session_id" IS '会话ID (外键关联chat_session)';
COMMENT ON COLUMN "public"."chat_message"."role" IS '消息角色: user(用户), assistant(AI助手), system(系统)';
COMMENT ON COLUMN "public"."chat_message"."content" IS '消息内容';
COMMENT ON COLUMN "public"."chat_message"."embedding" IS '向量嵌入 (用于语义检索，预留字段)';
COMMENT ON COLUMN "public"."chat_message"."created_at" IS '创建时间';

ALTER TABLE "public"."chat_message" ADD CONSTRAINT "chat_message_pkey" PRIMARY KEY ("id");

-- chat_message 表索引：会话ID索引，便于按会话查询消息列表
CREATE INDEX idx_chat_message_session_id ON "public"."chat_message" ("session_id");

-- chat_message 表索引：创建时间索引，用于滑动窗口查询优化
CREATE INDEX idx_chat_message_created_at ON "public"."chat_message" ("session_id", "created_at" DESC);




-- ============================================
-- 敏感词表 (sensitive_word) 表结构
-- ============================================
DROP TABLE IF EXISTS "public"."sensitive_word";
CREATE TABLE "public"."sensitive_word" (
                                           "id" "pg_catalog"."int4" NOT NULL,
                                           "word" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                           "category" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                           "status" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                           "created_at" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                           "updated_at" "pg_catalog"."varchar" COLLATE "pg_catalog"."default"
)
;
COMMENT ON TABLE "public"."sensitive_word" IS '敏感词表，存储系统敏感词过滤规则';
COMMENT ON COLUMN "public"."sensitive_word"."id" IS 'id';
COMMENT ON COLUMN "public"."sensitive_word"."word" IS '敏感词内容';
COMMENT ON COLUMN "public"."sensitive_word"."category" IS '敏感词类别';
COMMENT ON COLUMN "public"."sensitive_word"."status" IS '敏感词状态';
COMMENT ON COLUMN "public"."sensitive_word"."created_at" IS '创建时间戳';
COMMENT ON COLUMN "public"."sensitive_word"."updated_at" IS '更新时间戳';

-- ----------------------------
-- sensitive_word 表主键约束
-- ----------------------------
ALTER TABLE "public"."sensitive_word" ADD CONSTRAINT "sensitive_word_pkey" PRIMARY KEY ("id");




-- ============================================
-- 敏感词分类表 (sensitive_category) 表结构
-- ============================================
DROP TABLE IF EXISTS "public"."sensitive_category";
CREATE TABLE "public"."sensitive_category" (
                                               "id" "pg_catalog"."int4" NOT NULL,
                                               "category_name" "pg_catalog"."varchar" COLLATE "pg_catalog"."default",
                                               "created_time" "pg_catalog"."date",
                                               "update_time" "pg_catalog"."date",
                                               "status" "pg_catalog"."varchar" COLLATE "pg_catalog"."default"
)
;
COMMENT ON TABLE "public"."sensitive_category" IS '敏感词分类表，管理敏感词类别';
COMMENT ON COLUMN "public"."sensitive_category"."id" IS '主键ID';
COMMENT ON COLUMN "public"."sensitive_category"."category_name" IS '分类名';
COMMENT ON COLUMN "public"."sensitive_category"."created_time" IS '创建时间';
COMMENT ON COLUMN "public"."sensitive_category"."update_time" IS '更新时间';

-- ----------------------------
-- sensitive_category 表主键约束
-- ----------------------------
ALTER TABLE "public"."sensitive_category" ADD CONSTRAINT "sensitive_category_pkey" PRIMARY KEY ("id");