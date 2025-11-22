package top.mrxiaom.sweet.adaptiveshop;
        
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.mrxiaom.pluginbase.BukkitPlugin;
import top.mrxiaom.pluginbase.actions.ActionProviders;
import top.mrxiaom.pluginbase.economy.EnumEconomy;
import top.mrxiaom.pluginbase.economy.IEconomy;
import top.mrxiaom.pluginbase.func.LanguageManager;
import top.mrxiaom.pluginbase.paper.PaperFactory;
import top.mrxiaom.pluginbase.resolver.DefaultLibraryResolver;
import top.mrxiaom.pluginbase.utils.ClassLoaderWrapper;
import top.mrxiaom.pluginbase.utils.Util;
import top.mrxiaom.pluginbase.utils.inventory.InventoryFactory;
import top.mrxiaom.pluginbase.utils.item.ItemEditor;
import top.mrxiaom.pluginbase.utils.scheduler.FoliaLibScheduler;
import top.mrxiaom.sweet.adaptiveshop.actions.ActionGive;
import top.mrxiaom.sweet.adaptiveshop.actions.ActionRefresh;
import top.mrxiaom.sweet.adaptiveshop.database.BuyCountDatabase;
import top.mrxiaom.sweet.adaptiveshop.database.BuyShopDatabase;
import top.mrxiaom.sweet.adaptiveshop.database.OrderDatabase;
import top.mrxiaom.sweet.adaptiveshop.database.SellShopDatabase;
import top.mrxiaom.sweet.adaptiveshop.mythic.IMythic;
import top.mrxiaom.sweet.adaptiveshop.mythic.Mythic4;
import top.mrxiaom.sweet.adaptiveshop.mythic.Mythic5;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.concurrent.*;

public class SweetAdaptiveShop extends BukkitPlugin {
    public static SweetAdaptiveShop getInstance() {
        return (SweetAdaptiveShop) BukkitPlugin.getInstance();
    }

    public SweetAdaptiveShop() throws Exception {
        super(options()
                .bungee(false)
                .adventure(true)
                .database(true)
                .reconnectDatabaseWhenReloadConfig(false)
                .economy(EnumEconomy.VAULT)
                .scanIgnore("top.mrxiaom.sweet.adaptiveshop.libs")
        );
        this.scheduler = new FoliaLibScheduler(this);

        info("正在检查依赖库状态");
        File librariesDir = ClassLoaderWrapper.isSupportLibraryLoader
                ? new File("libraries")
                : new File(this.getDataFolder(), "libraries");
        DefaultLibraryResolver resolver = new DefaultLibraryResolver(getLogger(), librariesDir);

        resolver.addLibrary(BuildConstants.LIBRARIES);

        List<URL> libraries = resolver.doResolve();
        info("正在添加 " + libraries.size() + " 个依赖库到类加载器");
        for (URL library : libraries) {
            this.classLoader.addURL(library);
        }
    }

    @Override
    protected @NotNull ClassLoaderWrapper initClassLoader(URLClassLoader classLoader) {
        return ClassLoaderWrapper.isSupportLibraryLoader
                ? new ClassLoaderWrapper(ClassLoaderWrapper.findLibraryLoader(classLoader))
                : new ClassLoaderWrapper(classLoader);
    }

    @NotNull
    public IEconomy getEconomy() {
        return options.economy();
    }

    @Override
    public @NotNull InventoryFactory initInventoryFactory() {
        return PaperFactory.createInventoryFactory();
    }

    @Override
    public @NotNull ItemEditor initItemEditor() {
        return PaperFactory.createItemEditor();
    }

    private IMythic mythic;
    private BuyShopDatabase buyShopDatabase;
    private SellShopDatabase sellShopDatabase;
    private OrderDatabase orderDatabase;
    public BuyCountDatabase buyCountDatabase;
    private boolean supportTranslatable;
    private boolean supportOffHand;
    private boolean supportItemsAdder;
    private boolean uuidMode;
    
    // 添加异步执行器和超时调度器
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(1);

    public boolean isSupportTranslatable() {
        return supportTranslatable;
    }

    public boolean isSupportOffHand() {
        return supportOffHand;
    }

    public boolean isSupportItemsAdder() {
        return supportItemsAdder;
    }

    public void setUuidMode(boolean uuidMode) {
        this.uuidMode = uuidMode;
    }

    public boolean isUuidMode() {
        return uuidMode;
    }

    public BuyShopDatabase getBuyShopDatabase() {
        return buyShopDatabase;
    }

    public SellShopDatabase getSellShopDatabase() {
        return sellShopDatabase;
    }

    public OrderDatabase getOrderDatabase() {
        return orderDatabase;
    }

    public BuyCountDatabase getBuyCountDatabase() {
        return buyCountDatabase;
    }

    @Nullable
    public IMythic getMythic() {
        return mythic;
    }

    @Override
    protected void beforeLoad() {
        MinecraftVersion.replaceLogger(getLogger());
        MinecraftVersion.disableUpdateCheck();
        MinecraftVersion.disableBStats();
        MinecraftVersion.getVersion();

        supportTranslatable = Util.isPresent("org.bukkit.Translatable");
        supportOffHand = MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_9_R1);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
    }

    @Override
    protected void beforeEnable() {
        supportItemsAdder = Util.isPresent("dev.lone.itemsadder.api.CustomStack");
        Plugin mythicPlugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythicPlugin != null) {
            String ver = mythicPlugin.getDescription().getVersion();
            if (ver.startsWith("5.")) {
                mythic = new Mythic5();
            } else if (ver.startsWith("4.")) {
                mythic = new Mythic4();
            } else {
                mythic = null;
            }
        } else {
            mythic = null;
        }
        LanguageManager.inst().setLangFile("messages.yml")
                        .register(Messages.class, Messages::holder);
        options.registerDatabase(
                this.buyShopDatabase = new BuyShopDatabase(this),
                this.sellShopDatabase = new SellShopDatabase(this),
                this.orderDatabase = new OrderDatabase(this),
                this.buyCountDatabase = new BuyCountDatabase(this)
        );
        ActionProviders.registerActionProvider(ActionGive.PROVIDER);
        ActionProviders.registerActionProvider(ActionRefresh.PROVIDER);
    }

    @Override
    protected void afterEnable() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Placeholders(this).register();
        }
        getLogger().info("SweetAdaptiveShop 加载完毕");
    }

    @Override
    protected void onDisable() {
        // 关闭执行器
        asyncExecutor.shutdown();
        timeoutScheduler.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        super.onDisable();
    }

    /**
     * Java 8 兼容的超时包装方法
     */
    private <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit timeUnit) {
        final CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        // 设置超时任务
        final ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            if (!future.isDone()) {
                timeoutFuture.completeExceptionally(new TimeoutException("操作超时"));
            }
        }, timeout, timeUnit);
        
        // 当原始任务完成时，取消超时任务
        future.whenComplete((result, throwable) -> {
            timeoutTask.cancel(false);
            if (throwable != null) {
                timeoutFuture.completeExceptionally(throwable);
            } else {
                timeoutFuture.complete(result);
            }
        });
        
        return timeoutFuture;
    }

    /**
     * 安全地获取玩家名称（带超时保护）- Java 8 兼容版本
     */
    public CompletableFuture<String> getPlayerNameAsync(OfflinePlayer player) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                String name = player.getName();
                return name != null ? name : player.getUniqueId().toString();
            } catch (Exception e) {
                getLogger().warning("获取玩家名称失败: " + e.getMessage());
                return player.getUniqueId().toString();
            }
        }, asyncExecutor);
        
        return withTimeout(future, 3, TimeUnit.SECONDS);
    }
    
    /**
     * 安全执行数据库操作 - Java 8 兼容版本
     */
    public <T> CompletableFuture<T> executeDatabaseAsync(Supplier<T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } catch (Exception e) {
                getLogger().severe("数据库操作失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
        
        return withTimeout(future, 10, TimeUnit.SECONDS);
    }
    
    /**
     * 安全执行数据库操作（无返回值）- Java 8 兼容版本
     */
    public CompletableFuture<Void> executeDatabaseAsync(Runnable operation) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                operation.run();
            } catch (Exception e) {
                getLogger().severe("数据库操作失败: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, asyncExecutor);
        
        return withTimeout(future, 10, TimeUnit.SECONDS);
    }

    public String getDBKey(OfflinePlayer player) {
        if (isUuidMode()) {
            return player.getUniqueId().toString();
        } else {
            try {
                // 使用异步方式获取玩家名称，避免阻塞线程
                return getPlayerNameAsync(player).get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                getLogger().warning("获取玩家名称超时，使用UUID代替: " + player.getUniqueId());
                return player.getUniqueId().toString();
            }
        }
    }

    /**
     * 异步获取玩家数据库键
     */
    public CompletableFuture<String> getDBKeyAsync(OfflinePlayer player) {
        if (isUuidMode()) {
            return CompletableFuture.completedFuture(player.getUniqueId().toString());
        } else {
            return getPlayerNameAsync(player);
        }
    }
}