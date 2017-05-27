package cn.blmdz.aide.file.qcloud;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.request.UploadFileRequest;
import com.qcloud.cos.sign.Credentials;

import cn.blmdz.aide.file.FileServer;
import cn.blmdz.aide.file.exception.FileException;
import cn.blmdz.aide.file.util.FUtil;

public class QcloudFileServer implements FileServer {
	private static final Logger log = LoggerFactory.getLogger(QcloudFileServer.class);
	private final String bucketName;
	private final COSClient ossClient;

	public QcloudFileServer(long appId, String secretId, String secretKey, String region, String bucketName) {
		this.bucketName = bucketName;
		ClientConfig clientConfig = new ClientConfig();
		this.ossClient = new COSClient(clientConfig, new Credentials(appId, secretId, secretKey));
	}

	@Override
	public String write(String path, MultipartFile file) throws FileException {
		try {
			UploadFileRequest request = new UploadFileRequest(bucketName, path, file.getBytes());
			return ossClient.uploadFile(request);
		} catch (IOException e) {
			log.error("failed to upload file(path={}) to oss, cause:{}", path, Throwables.getStackTraceAsString(e));
			throw new FileException(e);
		}
	}

	@Override
	public String write(String path, File file) throws FileException {
		try {
			UploadFileRequest request = new UploadFileRequest(bucketName, path, Files.toByteArray(file));
			return ossClient.uploadFile(request);
		} catch (IOException e) {
			log.error("failed to upload file(path={}) to oss, cause:{}", path, Throwables.getStackTraceAsString(e));
			throw new FileException(e);
		}
	}

	@Override
	public String write(String path, InputStream inputStream) throws FileException {
		try {
			UploadFileRequest request = new UploadFileRequest(bucketName, path, FUtil.file(inputStream).getBytes());
			return ossClient.uploadFile(request);
		} catch (Exception e) {
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
