package cn.blmdz.aide.file.qiniu;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Throwables;
import com.qiniu.common.QiniuException;
import com.qiniu.common.Zone;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;

import cn.blmdz.aide.file.FileServer;
import cn.blmdz.aide.file.exception.FileException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QiniuFileServer implements FileServer {
    
    private final String bucketName;
    private UploadManager uploadManager;
    private Auth auth;
    
    public QiniuFileServer(String zone, String accessKey, String secretKey, String bucketName) {
        Zone z = null;
        if ("0".equalsIgnoreCase(zone)) z = Zone.zone0();
        else if ("1".equalsIgnoreCase(zone)) z = Zone.zone1();
        else if ("2".equalsIgnoreCase(zone)) z = Zone.zone2();
        else if ("as0".equalsIgnoreCase(zone)) z = Zone.zoneAs0();
        else if ("na0".equalsIgnoreCase(zone)) z = Zone.zoneNa0();
        else Zone.autoZone();
        
        this.bucketName = bucketName;
        this.uploadManager = new UploadManager(new Configuration(z));
        this.auth = Auth.create(accessKey, secretKey);
    }

    @Override
    public String write(String path, MultipartFile file) throws FileException {
        
        try {
            uploadManager.put(file.getInputStream(), path, auth.uploadToken(bucketName), null, null);
            return path;
        } catch (IOException e) {
            log.error("failed to upload file(path={}) to oss, cause:{}", path, Throwables.getStackTraceAsString(e));
            throw new FileException(e);
        }
    }

    @Override
    public String write(String path, File file) throws FileException {
        try {
            uploadManager.put(file, path, auth.uploadToken(bucketName));
            return path;
        } catch (QiniuException e) {
            log.error("failed to upload file(path={}) to oss, cause:{}", path, Throwables.getStackTraceAsString(e));
            throw new FileException(e);
        }
    }

    @Override
    public String write(String path, InputStream inputStream) throws FileException {
        try {
            uploadManager.put(inputStream, path, auth.uploadToken(bucketName), null, null);
            return path;
        } catch (QiniuException e) {
            log.error("failed to upload file(path={}) to oss, cause:{}", path, Throwables.getStackTraceAsString(e));
            throw new FileException(e);
        }
    }

    @Override
    public boolean delete(String path) throws FileException {
        // TODO Auto-generated method stub
        return false;
    }

}
