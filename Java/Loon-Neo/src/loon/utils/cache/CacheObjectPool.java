/**
 * Copyright 2008 - 2019 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * @project loon
 * @author cping
 * @email：javachenpeng@yahoo.com
 * @version 0.5
 */
package loon.utils.cache;

import java.util.Iterator;

import loon.LSysException;
import loon.utils.OrderedSet;
import loon.utils.TArray;
import loon.utils.TimeUtils;

public class CacheObjectPool<T extends GCCache<T>> extends CacheObjectBase {

	private final OrderedSet<GCCache<T>> _objects;

	private final TArray<T> _releaseObjects;

	private final TArray<T> _cachedObjects;

	private final boolean _allowSpawn;

	private long _autoTime;

	private long _expireTime;

	private float _autoInterval;

	private int _capacity;

	private int _priority;

	private CacheType _type;

	public CacheObjectPool(String name, boolean allowMultiSpawn, long autoReleaseInterval, int capacity,
			long expireTime, int priority) {
		super(name, capacity, expireTime);
		this._objects = new OrderedSet<GCCache<T>>();
		this._releaseObjects = new TArray<T>();
		this._cachedObjects = new TArray<T>();
		this._allowSpawn = allowMultiSpawn;
		this._priority = priority;
		this._autoTime = 0l;
		this._autoInterval = autoReleaseInterval;
		this._type = new CacheType("CachePool", 0);
	}

	@Override
	public CacheType getObjectType() {
		return _type;
	}

	@Override
	public int getCount() {
		return _objects.size();
	}

	@Override
	public int canReleaseCount() {
		return _releaseObjects.size();
	}

	@Override
	public boolean isAllowMultiSpawn() {
		return _allowSpawn;
	}

	@Override
	public float getAutoReleaseInterval() {
		return _autoInterval;
	}

	@Override
	public void setAutoReleaseInterval(float interval) {
		this._autoInterval = interval;
	}

	@Override
	public int getPriority() {
		return _priority;
	}

	@Override
	public void setPriority(int priority) {
		this._priority = priority;
	}

	public void register(T obj, boolean spawned) {
		if (obj == null) {
			throw new LSysException("GCCache is null");
		}
		_objects.add(new GCCache<T>(obj, spawned));
		release();
	}

	public boolean canSpawn() {
		return canSpawn("unkown");
	}

	public boolean canSpawn(String name) {
		for (CacheObject<T> obj : _objects) {
			if (!obj.getName().equals(name)) {
				continue;
			}

			if (_allowSpawn || !obj.isUse()) {
				return true;
			}
		}

		return false;
	}

	public CacheObject<T> onSpawn() {
		return onSpawn("unkown");
	}

	public CacheObject<T> onSpawn(String name) {
		for (CacheObject<T> obj : _objects) {
			if (!obj.getName().equals(name)) {
				continue;
			}
			if (_allowSpawn || !obj.isUse()) {
				return obj.onSpawn();
			}
		}
		return null;
	}

	public void unspawn(T obj) {
		if (obj == null) {
			throw new LSysException("GCCache is null");
		}

		unspawn(obj.getTarget());
	}

	private GCCache<T> getObject(Object target) {
		for (GCCache<T> obj : _objects) {
			if (obj.peek().getTarget() == target) {
				return obj;
			}
		}

		return null;
	}

	public void unspawn(Object target) {
		if (target == null) {
			throw new LSysException("Target is invalid");
		}

		GCCache<T> obj = getObject(target);
		if (obj != null) {

			obj.onUnspawn();
			release();
		} else {
			new LSysException("Can not find target in object pool :" + getName());
		}
	}

	public void setLocked(T obj, boolean locked) {
		if (obj == null) {
			throw new LSysException("Object is null");
		}

		setLocked(obj.getTarget(), locked);
	}

	public void setLocked(Object target, boolean locked) {
		if (target == null) {
			throw new LSysException("Target is null");
		}

		GCCache<T> obj = getObject(target);
		if (obj != null) {
			obj.setLocked(locked);
		} else {
			throw new LSysException("Can not find target in object pool :" + getName());
		}
	}

	public void setPriority(T obj, int priority) {
		if (obj == null) {
			throw new LSysException("Object is null");
		}

		setPriority(obj.getTarget(), priority);
	}

	public void setPriority(Object target, int priority) {
		if (target == null) {
			throw new LSysException("Target is invalid.");
		}

		GCCache<T> obj = getObject(target);
		if (obj != null) {
			obj.setPriority(priority);
		} else {
			throw new LSysException("Can not find target in object pool :" + getName());
		}
	}

	public void release() {
		release(_objects.size() - _capacity);
	}

	private void getCanReleaseObjects(TArray<T> results) {
		if (results == null) {
			throw new LSysException("Results is null");
		}

		results.clear();
		for (GCCache<T> obj : _objects) {
			if (obj.isUse() || obj.isLocked() || !obj.getCustomFlag()) {
				continue;
			}

			results.add(obj.peek());
		}
	}

	private TArray<T> getDefaultReleaseObjectFilterCallback(TArray<T> candidateObjects, int toReleaseCount,
			long expireTime) {
		_cachedObjects.clear();

		if (expireTime > Long.MIN_VALUE) {
			for (int i = candidateObjects.size - 1; i >= 0; i--) {
				if (candidateObjects.items[i].getLastUseTime() <= expireTime) {
					_cachedObjects.add(candidateObjects.items[i]);
					candidateObjects.removeIndex(i);
					continue;
				}
			}

			toReleaseCount -= _cachedObjects.size;
		}

		for (int i = 0; toReleaseCount > 0 && i < candidateObjects.size; i++) {
			for (int j = i + 1; j < candidateObjects.size; j++) {
				if (candidateObjects.items[i].getPriority() > candidateObjects.items[j].getPriority()
						|| candidateObjects.items[i].getPriority() == candidateObjects.items[j].getPriority()
								&& candidateObjects.items[i].getLastUseTime() > candidateObjects.items[j]
										.getLastUseTime()) {
					T temp = candidateObjects.items[i];
					candidateObjects.items[i] = candidateObjects.items[j];
					candidateObjects.items[j] = temp;
				}
			}

			_cachedObjects.add(candidateObjects.items[i]);
			toReleaseCount--;
		}
		return _cachedObjects;
	}

	public void release(int toReleaseCount) {

		_autoTime = 0l;
		if (toReleaseCount < 0) {
			toReleaseCount = 0;
		}

		long expireTime = TimeUtils.millis() - _expireTime;

		getCanReleaseObjects(_releaseObjects);
		TArray<T> toReleaseObjects = getDefaultReleaseObjectFilterCallback(_releaseObjects, toReleaseCount, expireTime);
		if (toReleaseObjects == null || toReleaseObjects.size <= 0) {
			return;
		}

		for (T toReleaseObject : toReleaseObjects) {
			if (toReleaseObject == null) {
				throw new LSysException("Can not release null object");
			}

			boolean found = false;
			for (GCCache<T> obj : _objects) {
				if (obj.peek() != toReleaseObject) {
					continue;
				}

				_objects.remove(obj);
				obj.disposed(false);
				found = true;
				break;
			}

			if (!found) {
				throw new LSysException("Can not release object which is not found");
			}
		}
	}

	@Override
	public void releaseAll() {
		for (Iterator<GCCache<T>> it = _objects.iterator(); it.hasNext();) {
			GCCache<T> obj = it.next();
			if (obj.isUse() || obj.isLocked() || !obj.getCustomFlag()) {
				continue;
			}
			obj.disposed(false);
			_objects.remove(this);
		}
	}

	@Override
	public CacheObjectInfo[] getAllObjectInfos() {
		int index = 0;
		CacheObjectInfo[] results = new CacheObjectInfo[_objects.size()];
		for (GCCache<T> obj : _objects) {
			results[index++] = new CacheObjectInfo(obj.getName(), obj.isLocked(), obj.getCustomFlag(),
					obj.getPriority(), obj.getLastUseTime(), obj.getSpawnCount());
		}

		return results;
	}

	@Override
	public void update(long elapseSeconds, long realElapseSeconds) {
		_autoTime += realElapseSeconds;
		if (_autoTime < _autoInterval) {
			return;
		}
		release();
	}

	@Override
	public void shutdown() {
		for (Iterator<GCCache<T>> it = _objects.iterator(); it.hasNext();) {
			GCCache<T> obj = it.next();
			_objects.remove(obj);
			obj.disposed(true);
		}
	}

}
