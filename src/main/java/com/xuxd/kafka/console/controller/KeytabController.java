package com.xuxd.kafka.console.controller;

import com.xuxd.kafka.console.aspect.annotation.ControllerLog;
import com.xuxd.kafka.console.aspect.annotation.Permission;
import com.xuxd.kafka.console.service.KeytabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * keytab 文件上传/查看/删除。复用集群编辑权限。
 *
 * @author kerberos-integration
 **/
@RestController
@RequestMapping("/cluster/keytab")
public class KeytabController {

    @Autowired
    private KeytabService keytabService;

    @ControllerLog("上传keytab")
    @Permission({"op:cluster-switch:add", "op:cluster-switch:edit"})
    @PostMapping
    public Object upload(@RequestPart(value = "file") MultipartFile file) {
        return keytabService.upload(file);
    }

    @Permission({"op:cluster-switch:add", "op:cluster-switch:edit"})
    @GetMapping
    public Object list() {
        return keytabService.list();
    }

    @ControllerLog("删除keytab")
    @Permission({"op:cluster-switch:edit"})
    @DeleteMapping("/{fileId}")
    public Object delete(@PathVariable("fileId") String fileId) {
        return keytabService.delete(fileId);
    }
}
