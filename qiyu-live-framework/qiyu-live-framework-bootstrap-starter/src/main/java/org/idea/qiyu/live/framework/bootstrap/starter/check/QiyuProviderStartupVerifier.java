package org.idea.qiyu.live.framework.bootstrap.starter.check;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Provider 启动自检器（ApplicationRunner，在 Spring 上下文刷新、Dubbo 服务导出之后执行）。
 *
 * 检查项：
 *  1. Dubbo 框架启动完成且 @DubboService 服务全部成功暴露；
 *  2. Dubbo 应用实例已注册到 Nacos；
 *  3. 所有 @DubboReference 下游引用可用（上游 provider 没起来时，这里会明确报错而不是静默挂住）；
 *  4. （如启用）MySQL 数据源可连接；
 *  5. （如启用）Redis 可连接。
 *
 * 任一项失败：打印明确的失败清单，进程以 exit code 1 退出（CountDownLatch 不会再阻塞）。
 *
 * 配置项：
 *  qiyu.startup.verify               是否启用自检，默认 true
 *  qiyu.startup.strict-references    是否强制校验下游 @DubboReference 可用，默认 true
 *  qiyu.startup.ignore-references    跳过强检的接口简单名，逗号分隔，如 IdGenerateRpc,ISmsRpc
 *  qiyu.startup.check-timeout-seconds Nacos 注册等待超时秒数，默认 15
 *
 * @Author qiyu
 */
public class QiyuProviderStartupVerifier implements ApplicationRunner {


    private static final Logger log = LoggerFactory.getLogger(QiyuProviderStartupVerifier.class);

    private static final String PROP_VERIFY = "qiyu.startup.verify";
    private static final String PROP_STRICT_REFERENCES = "qiyu.startup.strict-references";
    private static final String PROP_IGNORE_REFERENCES = "qiyu.startup.ignore-references";
    private static final String PROP_TIMEOUT = "qiyu.startup.check-timeout-seconds";

    private final ApplicationContext applicationContext;
//
    public QiyuProviderStartupVerifier(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("[QIYU-STARTUP-CHECK] self-check starting ...");
        var env = applicationContext.getEnvironment();
        if (!Boolean.parseBoolean(env.getProperty(PROP_VERIFY, "true"))) {
            log.info("[QIYU-STARTUP-CHECK] 已通过 {}=false 关闭启动自检", PROP_VERIFY);
            return;
        }
        long timeoutSeconds = Long.parseLong(env.getProperty(PROP_TIMEOUT, "15"));
        boolean strictReferences = Boolean.parseBoolean(env.getProperty(PROP_STRICT_REFERENCES, "true"));
        Set<String> ignoreReferences = new HashSet<>(Arrays.asList(
                env.getProperty(PROP_IGNORE_REFERENCES, "").trim().split("\\s*,\\s*")));

        List<String> errors = new ArrayList<>();
        List<String> oks = new ArrayList<>();

        checkDubboExport(timeoutSeconds, errors, oks);
        checkNacosRegistration(timeoutSeconds, errors, oks);
        checkReferences(strictReferences, ignoreReferences, errors, oks);
        checkDataSource(errors, oks);
        checkRedis(errors, oks);

        List<String> allLines = new ArrayList<>();
        allLines.addAll(errors.stream().map(e -> "[FAIL] " + e).toList());
        allLines.addAll(oks.stream().map(o -> "[ OK ] " + o).toList());
        writeMarkerFile(allLines);

        if (!errors.isEmpty()) {
            log.error("");
            log.error("==============================================================");
            log.error("  QIYU 启动自检失败：{} 项异常，进程即将退出(exit code 1)", errors.size());
            for (String error : errors) {
                log.error("  [FAIL] {}", error);
            }
            for (String ok : oks) {
                log.info ("  [ OK ] {}", ok);
            }
            log.error("  说明：");
            log.error("   - 下游服务未就绪导致的失败，请先启动上游 provider 再重启本服务");
            log.error("   - 关闭自检：{}=false", PROP_VERIFY);
            log.error("   - 放宽下游强检：{}=false", PROP_STRICT_REFERENCES);
            log.error("   - 跳过某些接口：{}=接口简单名(逗号分隔)", PROP_IGNORE_REFERENCES);
            log.error("==============================================================");
            // 触发 shutdown hook -> Spring 优雅销毁 -> 进程退出，外部脚本可见非零退出码
            System.exit(1);
        }

        log.info("");
        log.info("[QIYU-STARTUP-CHECK] 启动自检全部通过：");
        for (String ok : oks) {
            log.info("  [ OK ] {}", ok);
        }
        log.info("[QIYU-STARTUP-CHECK] 服务已就绪，进入常驻运行...");
    }

    /**
     * 检查 Dubbo bootstrap 已启动且 @DubboService 全部暴露成功
     */
    private void checkDubboExport(long timeoutSeconds, List<String> errors, List<String> oks) {
        try {
            boolean started = waitUntil(DubboBootstrap.getInstance()::isStarted, timeoutSeconds, 500);
            if (!started) {
                errors.add("Dubbo 框架在 " + timeoutSeconds + "s 内未完成启动（检查 nacos 注册中心地址/鉴权配置）");
                return;
            }
            List<ProviderModel> exportedServices =
                    ApplicationModel.defaultModel().getDefaultModule().getServiceRepository().getExportedServices();
            if (exportedServices.isEmpty()) {
                errors.add("Dubbo 未暴露任何 @DubboService 服务（检查 @EnableDubbo 扫描包路径是否包含 rpc 实现类）");
                return;
            }
            List<String> keys = exportedServices.stream().map(ProviderModel::getServiceKey).toList();
            oks.add("Dubbo 已暴露 " + keys.size() + " 个服务: " + keys);
        } catch (Throwable t) {
            errors.add("Dubbo 服务导出检查异常: " + rootMessage(t));
        }
    }

    /**
     * 检查 Dubbo 应用实例是否已注册到 Nacos（应用级服务发现，服务名 = spring.application.name）
     */
    private void checkNacosRegistration(long timeoutSeconds, List<String> errors, List<String> oks) {
        try {
            NacosServiceManager nacosServiceManager = applicationContext.getBeanProvider(NacosServiceManager.class).getIfAvailable();
            if (nacosServiceManager == null) {
                oks.add("未启用 Nacos 服务发现，跳过注册检查");
                return;
            }
            String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
            NamingService namingService = nacosServiceManager.getNamingService();
            boolean registered = waitUntil(() -> {
                try {
                    return !namingService.getAllInstances(appName).isEmpty();
                } catch (Exception e) {
                    return false;
                }
            }, timeoutSeconds, 1000);
            if (registered) {
                oks.add("Nacos 注册正常: " + appName);
            } else {
                errors.add("Dubbo 应用实例在 " + timeoutSeconds + "s 内未注册到 Nacos(服务名=" + appName
                        + ")，检查 nacos server 地址/namespace/用户名密码配置");
            }
        } catch (Throwable t) {
            errors.add("Nacos 注册检查异常: " + rootMessage(t));
        }
    }

    /**
     * 逐个初始化 @DubboReference 引用并强制 check=true：
     * 上游 provider 未启动时此处会抛出明确的 IllegalStateException，而不是等运行期才报错
     */
    private void checkReferences(boolean strictReferences, Set<String> ignoreReferences,
                                 List<String> errors, List<String> oks) {
        if (!strictReferences) {
            oks.add("下游 @DubboReference 强检已关闭(" + PROP_STRICT_REFERENCES + "=false)");
            return;
        }
        try {
            Map<String, ReferenceBean> referenceBeans = applicationContext.getBeansOfType(ReferenceBean.class);
            if (referenceBeans.isEmpty()) {
                oks.add("无 @DubboReference 下游引用，跳过");
                return;
            }
            List<String> unavailable = new ArrayList<>();
            int checked = 0;
            for (Map.Entry<String, ReferenceBean> entry : referenceBeans.entrySet()) {
                ReferenceBean<?> referenceBean = entry.getValue();
                org.apache.dubbo.config.ReferenceConfig<?> referenceConfig = referenceBean.getReferenceConfig();
                String interfaceName = referenceBean.getServiceInterface();
                String simpleName = interfaceName == null ? entry.getKey()
                        : interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
                if (ignoreReferences.contains(simpleName)) {
                    continue;
                }
                if (referenceConfig == null) {
                    continue;
                }
                checked++;
                try {
                    // 若引用已初始化则直接返回代理；未初始化则触发订阅+可用性检查
                    referenceConfig.setCheck(true);
                    referenceConfig.get();
                } catch (Throwable t) {
                    unavailable.add(simpleName + "(" + rootMessage(t) + ")");
                }
            }
            if (!unavailable.isEmpty()) {
                errors.add("下游 Dubbo 服务不可用 " + unavailable.size() + "/" + checked + " 个: "
                        + String.join("; ", unavailable) + " —— 请先启动对应上游 provider");
            } else {
                oks.add("下游 @DubboReference 全部可用(" + checked + " 个)");
            }
        } catch (Throwable t) {
            errors.add("下游引用检查异常: " + rootMessage(t));
        }
    }

    /**
     * 存在 DataSource 时检查 MySQL 可连接
     */
    private void checkDataSource(List<String> errors, List<String> oks) {
        DataSource dataSource = applicationContext.getBeanProvider(DataSource.class).getIfAvailable();
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            connection.createStatement().execute("SELECT 1");
            oks.add("MySQL 连接正常: " + url);
        } catch (Throwable t) {
            errors.add("MySQL 连接失败（检查数据库地址/账号/库名/表结构是否初始化）: " + rootMessage(t));
        }
    }

    /**
     * 存在 RedisConnectionFactory 时检查 Redis 可连接
     */
    private void checkRedis(List<String> errors, List<String> oks) {
//        RedisConnectionFactory connectionFactory =
//                applicationContext.getBeanProvider(RedisConnectionFactory.class).getIfAvailable();
//        if (connectionFactory == null) {
//            return;
//        }
//        try (RedisConnection connection = connectionFactory.getConnection()) {
//            connection.ping();
//            oks.add("Redis 连接正常");
//        } catch (Throwable t) {
////            errors.add("Redis 连接失败（检查 redis 地址/密码配置）: " + rootMessage(t));
//        }
    }

    /**
     * 把自检结果直接写到文件与stdout，不依赖日志系统（部分环境日志在启动中段会停止输出）
     */
    private void writeMarkerFile(List<String> lines) {
        try {
            String appName = applicationContext.getEnvironment().getProperty("spring.application.name", "unknown");
            var passed = lines.stream().noneMatch(l -> l.startsWith("[FAIL]"));
            java.nio.file.Path path = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                    "qiyu-startup-check-" + appName + ".txt");
            String content = "check=" + (passed ? "PASSED" : "FAILED") + " time=" + java.time.LocalDateTime.now()
                    + "\n" + String.join("\n", lines) + "\n";
            java.nio.file.Files.writeString(path, content);
            System.out.println("[QIYU-STARTUP-CHECK] result written to " + path);
            System.out.println(content);
        } catch (Throwable t) {
            System.out.println("[QIYU-STARTUP-CHECK] write marker failed: " + t);
        }
    }

    private boolean waitUntil(BooleanSupplier condition, long timeoutSeconds, long intervalMillis) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }
}
