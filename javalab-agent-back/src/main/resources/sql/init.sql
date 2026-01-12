-- ============================================
-- 初始化 PostgreSQL 扩展
-- ============================================
CREATE EXTENSION IF NOT EXISTS vector;      -- 向量存储扩展，用于AI向量检索
CREATE EXTENSION IF NOT EXISTS hstore;      -- 键值对存储扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- UUID生成扩展

-- ============================================
-- 向量存储表 (vector_store) - 用于RAG知识库向量存储
-- ============================================
CREATE TABLE public.vector_store (
                                     id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
                                     content text,
                                     metadata jsonb,
                                     embedding public.vector(1024),
                                     CONSTRAINT vector_store_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE vector_store IS '向量存储表，用于RAG知识库的向量检索';
COMMENT ON COLUMN vector_store.id IS '主键ID (UUID)';
COMMENT ON COLUMN vector_store.content IS '文本内容';
COMMENT ON COLUMN vector_store.metadata IS '元数据信息 (JSONB格式)';
COMMENT ON COLUMN vector_store.embedding IS '向量嵌入 (1024维，用于相似度检索)';

-- 创建HNSW索引，用于向量余弦相似度检索
CREATE INDEX vector_store_embedding_idx ON public.vector_store USING hnsw (embedding public.vector_cosine_ops);

-- ============================================
-- 用户表 (tb_user) 表结构
-- ============================================
DROP TABLE IF EXISTS "public"."tb_user";
CREATE TABLE public.tb_user (
                                id integer NOT NULL,
                                name character varying NOT NULL,
                                user_name character varying NOT NULL,
                                password character varying NOT NULL,
                                phone character varying,
                                sex character varying,
                                id_number character varying,
                                status integer DEFAULT 1 NOT NULL,
                                create_time date,
                                update_time date,
                                create_user bigint,
                                update_user bigint
);


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

CREATE TABLE public.ali_oss_file (
                                     id bigint NOT NULL,
                                     file_name character varying,
                                     url character varying,
                                     vector_id text,
                                     create_time timestamp without time zone,
                                     update_time timestamp without time zone
);
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

CREATE TABLE public.chat_session (
                                     id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
                                     user_id bigint,
                                     title character varying(255),
                                     created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                                     updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                                     deleted smallint DEFAULT 0
);
COMMENT ON TABLE "public"."chat_session" IS '对话会话表';
COMMENT ON COLUMN "public"."chat_session"."id" IS '会话ID (UUID)';
COMMENT ON COLUMN "public"."chat_session"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."chat_session"."title" IS '会话标题';
COMMENT ON COLUMN "public"."chat_session"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."chat_session"."updated_at" IS '更新时间';
COMMENT ON COLUMN "public"."chat_session"."deleted" IS '逻辑删除标记: 0-未删除, 1-已删除';

ALTER TABLE "public"."chat_session" ADD CONSTRAINT "chat_session_pkey" PRIMARY KEY ("id");

CREATE INDEX idx_chat_session_user_id ON public.chat_session USING btree (user_id);


-- ============================================
-- 消息表 (messages) - 用于存储对话消息记录
-- ============================================
DROP TABLE IF EXISTS "public"."chat_message";
CREATE TABLE public.chat_message (
                                     id bigint NOT NULL,
                                     session_id uuid NOT NULL,
                                     role character varying(20) NOT NULL,
                                     content text,
                                     embedding public.vector(1024),
                                     created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
                                     user_id bigint NOT NULL
);
COMMENT ON TABLE "public"."chat_message" IS '对话消息表';
COMMENT ON COLUMN "public"."chat_message"."id" IS '消息ID';
COMMENT ON COLUMN "public"."chat_message"."session_id" IS '会话ID (外键关联chat_session)';
COMMENT ON COLUMN "public"."chat_message"."user_id" IS '用户ID (用于消息级别隔离)';
COMMENT ON COLUMN "public"."chat_message"."role" IS '消息角色: user(用户), assistant(AI助手), system(系统)';
COMMENT ON COLUMN "public"."chat_message"."content" IS '消息内容';
COMMENT ON COLUMN "public"."chat_message"."created_at" IS '创建时间';
COMMENT ON COLUMN public.chat_message.embedding IS '向量嵌入 (用于语义检索，预留字段)';


ALTER TABLE "public"."chat_message" ADD CONSTRAINT "chat_message_pkey" PRIMARY KEY ("id");

CREATE INDEX idx_chat_message_user_id ON public.chat_message USING btree (user_id);
CREATE INDEX idx_chat_message_session_id ON public.chat_message USING btree (session_id);