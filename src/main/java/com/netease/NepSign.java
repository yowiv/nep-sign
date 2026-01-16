package com.netease;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网易大神签名算法 - Unidbg 调用 libnep.so
 * 
 * 已解析的函数地址:
 * - getPostMethodSignatures: 0x200b0c
 * - getGetMethodSignatures:  0x20068c
 */
public class NepSign extends AbstractJni {
    
    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;
    private final DvmClass NepTools;
    
    // 从内存 dump 解析出的函数偏移
    private static final long FUNC_GET_POST_SIGNATURES = 0x200b0c;
    private static final long FUNC_GET_GET_SIGNATURES = 0x20068c;
    
    public NepSign() throws IOException {
        // 创建 Android 模拟器 (ARM64)
        emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.netease.gl")
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        
        // 配置内存
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23)); // Android 6.0
        
        // 创建 Dalvik 虚拟机
        vm = emulator.createDalvikVM();
        vm.setJni(this);
        vm.setVerbose(false); // 关闭详细日志
        
        // 加载 libnep.so (从内存 dump 的版本)
        DalvikModule dm = vm.loadLibrary(new File("src/main/resources/libnep.so"), false);
        module = dm.getModule();
        
        System.out.println("[+] libnep.so 加载成功!");
        System.out.println("[+] 模块基址: 0x" + Long.toHexString(module.base));
        System.out.println("[+] 模块大小: " + module.size + " bytes");
        
        // 获取 Tools 类
        NepTools = vm.resolveClass("com/netease/nep/Tools");
        
        // 尝试调用 JNI_OnLoad (可能会失败，如果 so 结构损坏)
        try {
            dm.callJNI_OnLoad(emulator);
            System.out.println("[+] JNI_OnLoad 调用成功");
        } catch (Exception e) {
            System.out.println("[!] JNI_OnLoad 调用失败 (可能是正常的): " + e.getMessage());
        }
    }
    
    /**
     * 直接调用 native 函数 (使用手动计算的地址)
     */
    public String callNativePostSign(String url, String content) {
        // 计算函数实际地址
        long funcAddr = module.base + FUNC_GET_POST_SIGNATURES;
        
        System.out.println("[*] 调用 getPostMethodSignatures");
        System.out.println("[*] 函数地址: 0x" + Long.toHexString(funcAddr));
        
        // 创建参数
        StringObject urlObj = new StringObject(vm, url);
        StringObject contentObj = new StringObject(vm, content);
        
        // 使用 JNI 调用方式
        // 参数: JNIEnv*, jclass, jstring url, jstring content
        List<Object> args = new ArrayList<>();
        args.add(vm.getJNIEnv());  // JNIEnv*
        args.add(0);  // jclass (静态方法可以为0)
        args.add(vm.addLocalObject(urlObj));  // url
        args.add(vm.addLocalObject(contentObj));  // content
        
        // 调用函数
        Number result = module.callFunction(emulator, funcAddr, args.toArray());
        
        if (result != null && result.longValue() != 0) {
            DvmObject<?> obj = vm.getObject(result.intValue());
            if (obj instanceof StringObject) {
                return ((StringObject) obj).getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 调用签名方法 - 通过符号表 (如果可用)
     */
    public String getPostMethodSignatures(String url, String content) {
        try {
            StringObject result = NepTools.callStaticJniMethodObject(
                    emulator,
                    "getPostMethodSignatures(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    vm.addLocalObject(new StringObject(vm, url)),
                    vm.addLocalObject(new StringObject(vm, content))
            );
            return result != null ? result.getValue() : null;
        } catch (Exception e) {
            System.out.println("[!] 符号调用失败，尝试直接地址调用: " + e.getMessage());
            return callNativePostSign(url, content);
        }
    }
    
    /**
     * GET 请求签名
     */
    public String getGetMethodSignatures(String url) {
        long funcAddr = module.base + FUNC_GET_GET_SIGNATURES;
        
        StringObject urlObj = new StringObject(vm, url);
        
        List<Object> args = new ArrayList<>();
        args.add(vm.getJNIEnv());
        args.add(0);
        args.add(vm.addLocalObject(urlObj));
        args.add(0);  // headers HashMap, 传 null
        
        Number result = module.callFunction(emulator, funcAddr, args.toArray());
        
        if (result != null && result.longValue() != 0) {
            DvmObject<?> obj = vm.getObject(result.intValue());
            if (obj instanceof StringObject) {
                return ((StringObject) obj).getValue();
            }
        }
        
        return null;
    }
    
    /**
     * 关闭模拟器
     */
    public void close() throws IOException {
        emulator.close();
    }
    
    // ==================== JNI 回调实现 ====================
    
    @Override
    public DvmObject<?> callStaticObjectMethod(BaseVM vm, DvmClass dvmClass, String signature, VarArg varArg) {
        System.out.println("[JNI] callStaticObjectMethod: " + signature);
        return super.callStaticObjectMethod(vm, dvmClass, signature, varArg);
    }
    
    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        System.out.println("[JNI] callObjectMethod: " + signature);
        
        if (signature.equals("java/util/HashMap->keySet()Ljava/util/Set;")) {
            return vm.resolveClass("java/util/HashSet").newObject(null);
        }
        
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }
    
    @Override
    public int callIntMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        System.out.println("[JNI] callIntMethod: " + signature);
        return super.callIntMethod(vm, dvmObject, signature, varArg);
    }
    
    // ==================== 测试入口 ====================
    
    public static void main(String[] args) throws IOException {
        System.out.println("========================================");
        System.out.println("    网易大神签名测试 (Unidbg)");
        System.out.println("========================================\n");
        
        NepSign nep = new NepSign();
        
        // 测试签名
        String url = "https://god.gameyw.netease.com/v1/welfare/client/getAppInfo";
        String content = "{\"appKey\":\"dashen\"}";
        
        System.out.println("\n[*] 测试 POST 签名...");
        System.out.println("[*] URL: " + url);
        System.out.println("[*] Content: " + content);
        
        String result = nep.getPostMethodSignatures(url, content);
        System.out.println("\n[*] 签名结果: " + result);
        
        if (result != null) {
            System.out.println("\n[+] ✓ 签名成功!");
        } else {
            System.out.println("\n[-] ✗ 签名失败");
        }
        
        nep.close();
    }
}
