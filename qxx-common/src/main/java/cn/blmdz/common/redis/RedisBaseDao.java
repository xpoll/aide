package cn.blmdz.common.redis;

import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import cn.blmdz.common.redis.JedisExecutor.JedisCallBack;
import cn.blmdz.common.redis.JedisExecutor.JedisCallBackNoResult;
import cn.blmdz.common.serialize.StringHashMapper;
import cn.blmdz.common.util.Joiners;
import cn.blmdz.common.util.KeyUtils;
import cn.blmdz.common.util.Splitters;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

public abstract class RedisBaseDao<T> {

    public final StringHashMapper<T> stringHashMapper;
    protected final JedisExecutor jedisExecutor;
    protected final Class<T> entityClass;
    protected final int select;

	@SuppressWarnings("unchecked")
	public RedisBaseDao(JedisExecutor jedisExecutor, Integer select) {
        this.jedisExecutor = jedisExecutor;
        this.select = select == null ? 0 : select.intValue();
        entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        stringHashMapper = new StringHashMapper<T>(entityClass);
    }

    /**
     * 主键ID
     */
    protected Long newId(){
        return jedisExecutor.execute(new JedisCallBack<Long>() {
            @Override
            public Long execute(Jedis jedis) {
                return jedis.incr(KeyUtils.entityCount(entityClass));
            }
        }, select);
    }
    
	/**
	 * 序列key->一对一
	 */
	protected String indexOne(String index) {
		return KeyUtils.entityIndexOne(entityClass, index);
	}
	
	/**
	 * 序列key->一对多
	 */
	protected String indexMore(String index) {
		return KeyUtils.entityIndexMore(entityClass, index);
	}

    /**
     * 根据主键ID查找
     */
    public T findById(final Long id) {
    	return findByKey(KeyUtils.entityId(entityClass,id));
    }
    
    /**
     * 根据主键ID查找
     */
    public List<T> findByIds(final Iterable<Long> ids) {
    	return findByKeys(ids);
    }
	
    /**
	 * 根据外键获取对象->一对一
	 */
	protected T findIndexOne(final String key, final String index) {
		if (Strings.isNullOrEmpty(key)) return null;
		
		Map<String, String> hash = jedisExecutor.execute(new JedisCallBack<Map<String, String>>() {
			@Override
			public Map<String, String> execute(Jedis jedis) {
				String id = jedis.hget(index, key);
				if (Strings.isNullOrEmpty(id)) return null;
				return jedis.hgetAll(KeyUtils.entityId(entityClass, id));
			}
		}, select);

        return stringHashMapper.fromHash(hash);
	}
	
	/**
	 * 外键一对多的存储
	 */
	protected String newIndexMore(final String key, final Long oldValue, final Long newValue, final String index) {
		return jedisExecutor.execute(new JedisCallBack<String>() {
			@Override
			public String execute(Jedis jedis) {
				String sequence = jedis.hget(index, key);
				if (Strings.isNullOrEmpty(sequence)) return null;
				List<Long> ids = Splitters.splitToLong(sequence, Splitters.COMMA);
				Set<Long> set = Sets.newTreeSet(ids);
				if (oldValue != null) {
					set.remove(oldValue);
				}
				set.add(newValue);
				return Joiners.COMMA.join(set);
			}
		}, select);
	}
	
	/**
	 * 根据外键获取对象->一对多
	 */
	protected T findIndexMore(final String key, final Long value, final String index) {
		if (Strings.isNullOrEmpty(key) || value == null) return null;
		
		Map<String, String> hash = jedisExecutor.execute(new JedisCallBack<Map<String, String>>() {
			@Override
			public Map<String, String> execute(Jedis jedis) {
				String sequence = jedis.hget(index, key);
				if (Strings.isNullOrEmpty(sequence)) return null;
				List<Long> ids = Splitters.splitToLong(sequence, Splitters.COMMA);
				for (Long id : ids) {
					if (id == null) return null;
					if (Objects.equal(value, id)) return jedis.hgetAll(KeyUtils.entityId(entityClass, id)); 
				}
				return null;
			}
		}, select);

        return stringHashMapper.fromHash(hash);
	}
	
	/**
	 * 根据外键获取对象->一对多
	 */
	protected List<T> findIndexMore(final String key, final String index) {
		if (Strings.isNullOrEmpty(key)) return null;

        List<Response<Map<String, String>>> result = jedisExecutor.execute(new JedisCallBack<List<Response<Map<String, String>>>>() {
        	@Override
            public List<Response<Map<String, String>>> execute(Jedis jedis) {
        		String sequence = jedis.hget(index, key);
        		if (!Strings.isNullOrEmpty(sequence)) return Collections.emptyList();
        		List<Long> ids = Splitters.splitToLong(sequence, Splitters.COMMA);
            	List<Response<Map<String, String>>> result = Lists.newArrayListWithCapacity(Iterables.size(ids));
            	
                Pipeline p = jedis.pipelined();
                for (Long key : ids) {
                	result.add(p.hgetAll(KeyUtils.entityId(entityClass, key)));
				}
                p.sync();
                return result;
            }
        }, select);
        
        List<T> entities = Lists.newArrayListWithCapacity(result.size());
        for (Response<Map<String, String>> t : result) {
        	entities.add(this.stringHashMapper.fromHash(t.get()));
		}
        return entities;
	}
	
	/**
	 * 创建->需要实现create
	 */
	public void create(final T obj) {
		jedisExecutor.execute(new JedisCallBackNoResult() {
			@Override
			public void execute(Jedis jedis) {
				Transaction t = jedis.multi();
				create(t, obj);
				t.exec();
			}
		}, select);
	}
	
	/**
	 * 更新->需要实现update
	 */
	public void update(final T obj) {
		jedisExecutor.execute(new JedisCallBackNoResult(){
			@Override
			public void execute(Jedis jedis) {
				Transaction t = jedis.multi();
				update(t, obj);
				t.exec();
			}
		}, select);
	}

	protected abstract void create(Transaction t, T obj);
	
	protected abstract void update(Transaction t, T obj);
    
    private T findByKey(final String key) {
        Map<String, String> hash = jedisExecutor.execute(new JedisCallBack<Map<String, String>>() {
        	@Override
            public Map<String, String> execute(Jedis jedis) {
                return jedis.hgetAll(key);
            }
        }, select);
        return this.stringHashMapper.fromHash(hash);
    }

    private List<T> findByKeys(final Iterable<Long> keys) {
        if(Iterables.isEmpty(keys)) {
            return Collections.emptyList();
        } else {
            List<Response<Map<String, String>>> result = jedisExecutor.execute(new JedisCallBack<List<Response<Map<String, String>>>>() {
            	@Override
                public List<Response<Map<String, String>>> execute(Jedis jedis) {
                	List<Response<Map<String, String>>> result = Lists.newArrayListWithCapacity(Iterables.size(keys));
                    Pipeline p = jedis.pipelined();
                    for (Long key : keys) {
                    	result.add(p.hgetAll(KeyUtils.entityId(entityClass, key)));
					}
                    p.sync();
                    return result;
                }
            }, select);
            List<T> entities = Lists.newArrayListWithCapacity(result.size());
            for (Response<Map<String, String>> t : result) {
            	entities.add(this.stringHashMapper.fromHash(t.get()));
			}
            return entities;
        }
    }
}
