/**
 * Copyright 2014
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
 * @email javachenpeng@yahoo.com
 * @version 0.4.2
 */
package loon.action.map;

import java.util.Iterator;

import loon.LObject;
import loon.LSystem;
import loon.LTexture;
import loon.PlayerUtils;
import loon.LTexture.Format;
import loon.Screen;
import loon.action.ActionBind;
import loon.action.ActionTween;
import loon.action.map.colider.TileImpl;
import loon.action.sprite.Animation;
import loon.action.sprite.ISprite;
import loon.action.sprite.MoveControl;
import loon.action.sprite.Sprites;
import loon.canvas.Image;
import loon.canvas.LColor;
import loon.canvas.Pixmap;
import loon.event.DrawListener;
import loon.font.FontSet;
import loon.font.IFont;
import loon.geom.Affine2f;
import loon.geom.Polygon;
import loon.geom.RectBox;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.opengl.LTexturePack;
import loon.opengl.LTexturePackClip;
import loon.utils.CollectionUtils;
import loon.utils.IntHashMap;
import loon.utils.IntHashMap.Entry;
import loon.utils.MathUtils;
import loon.utils.SortedList;
import loon.utils.StringUtils;
import loon.utils.TArray;

public class HexagonMap extends LObject<ISprite> implements FontSet<HexagonMap>, ISprite {

	public static final int LEFT = -3;
	public static final int DOWNLEFT = -2;
	public static final int UPLEFT = -1;
	public static final int NONE = 0;
	public static final int DOWNRIGHT = 1;
	public static final int UPRIGHT = 2;
	public static final int RIGHT = 3;

	private boolean allowDisplayFindPath;

	private boolean allowDisplayClicked;

	private boolean allowDisplayPosition;

	private boolean allowDisplayPosText;

	private boolean active = false;

	private boolean visible = false;

	private RectBox rectTemp = null;

	private RectBox viewRect = null;

	private Vector2f positionFlag = null;

	private LTexturePack texturePack;

	private AStarFindHeuristic heuristic = null;

	private SortedList<int[]> focuses;

	private Format format = Format.LINEAR;

	protected int cols, rows;

	protected TileImpl[][] tiles;

	protected Hexagon origin;

	protected Hexagon[][] hexagons;

	private Field2D field2dMap;

	private int[] position = new int[2];

	private int[][] positions = new int[6][2];

	private int lastOffsetX, lastOffsetY;

	private RectBox rectViewTemp = null;

	private ActionBind follow;

	private LColor fontColor = LColor.black.cpy();

	private TArray<TileImpl> tileBinds = new TArray<TileImpl>(12);

	private boolean playAnimation;

	private IntHashMap textureCaches = new IntHashMap();

	private boolean roll;

	private boolean dirty;

	private TArray<Animation> animations = new TArray<Animation>();

	private IFont displayFont;

	private Vector2f offset = new Vector2f(0, 0);

	private DrawListener<HexagonMap> listener;

	private LColor _baseColor;

	private LTexture _background;

	private float _scaleX, _scaleY;

	private Sprites _mapSprites;

	private Sprites _sprites;

	protected static class Node implements Comparable<Node> {

		protected int[] position;
		protected int f, g, h;
		protected Node parent;

		public Node(int[] position) {
			this.position = CollectionUtils.copyOf(position);
		}

		public boolean equals(int[] position) {
			return this.position[0] == position[0] && this.position[1] == position[1];
		}

		@Override
		public int compareTo(Node another) {
			int result = this.f - another.f;
			if (result == 0) {
				result = this.h - another.h;
			}
			return result;
		}
	}

	protected static class NodeList extends TArray<Node> {

		public Node find(int[] position) {
			for (int i = size - 1; i >= 0; i--) {
				Node node = get(i);
				if (node.equals(position)) {
					return node;
				}
			}
			return null;
		}

		public void insert(Node node) {
			for (int i = size; i > 0; i--) {
				Node n = get(i - 1);
				if (node.compareTo(n) >= 0) {
					insert(i, node);
					return;
				}
			}
			insert(0, node);
		}

		public void sort(Node node) {
			for (int i = size - 1; i >= 0; i--) {
				Node n = get(i);
				if (n == node) {
					for (; i > 0; i--) {
						n = get(i - 1);
						if (node.compareTo(n) >= 0) {
							return;
						} else {
							set(i, n);
							set(i - 1, node);
						}
					}
				}
			}
		}
	}

	public static class Path {
		public SortedList<int[]> positions;
		public int cost;
	}

	protected class CellIterator implements Iterator<TileVisit<TileImpl>> {

		protected int m, n;
		protected int cols, rows;
		protected int i, j, k;

		protected CellIterator(int m, int n, int cols, int rows) {
			this.m = m;
			this.n = n;
			this.cols = cols;
			this.rows = rows;
			k = n >> 1;
		}

		@Override
		public boolean hasNext() {
			return i < cols && j < rows;
		}

		@Override
		public TileVisit<TileImpl> next() {
			TileVisit<TileImpl> tileVisit = new TileVisit<TileImpl>();
			tileVisit.tile = (TileImpl) tiles[i + m][j + n];
			tileVisit.position[0] = i + m - k;
			tileVisit.position[1] = j + n;
			if (++i >= cols) {
				k = (++j + n) >> 1;
				i = 0;
			}
			return tileVisit;
		}

		@Override
		public void remove() {
		}

	}

	public Iterator<TileVisit<TileImpl>> iterator() {
		return new CellIterator(0, 0, cols, rows);
	}

	public Iterable<TileVisit<TileImpl>> allTiles(RectBox inRect) {
		int m = inRect.Left() / (origin.getStartWidth() + origin.getStartWidth()) - 1;
		if (m < 0) {
			m = 0;
		}
		int n = inRect.Top() / (origin.getEndHeight() + origin.getMidHeight()) - 1;
		if (n < 0) {
			n = 0;
		}
		int cols = inRect.width / (origin.getStartWidth() + origin.getStartWidth()) + 3;
		if (m + cols >= this.cols) {
			cols = this.cols - m;
		}
		int rows = inRect.height / (origin.getEndHeight() + origin.getMidHeight()) + 3;
		if (n + rows >= this.rows) {
			rows = this.rows - n;
		}
		final CellIterator iterator = new CellIterator(m, n, cols, rows);
		return new Iterable<TileVisit<TileImpl>>() {

			@Override
			public Iterator<TileVisit<TileImpl>> iterator() {
				return iterator;
			}
		};
	}

	public static final int[][] orientationsByHexagon = { { LEFT, UPLEFT, UPRIGHT }, { LEFT, NONE, RIGHT },
			{ DOWNLEFT, DOWNRIGHT, RIGHT } };

	public static final int[][] hexagonByOrientation = { { -1, 0 }, // 左
			{ -1, 1 }, // 下左
			{ 0, -1 }, // 上左
			{ 0, 0 }, // 无
			{ 0, 1 }, // 下右
			{ 1, -1 }, // 上右
			{ 1, 0 }, // 右
	};

	public static Path findPath(HexagonMap map, Vector2f start, Vector2f end) {
		return findPath(map, start.toInt(), end.toInt(), 0);
	}

	public static Path findPath(HexagonMap map, int[] start, int[] end) {
		return findPath(map, start, end, 0);
	}

	public static Path findPath(HexagonMap map, Vector2f start, Vector2f end, int endRadius) {
		return findPath(map, start.toInt(), end.toInt(), endRadius);
	}

	public static Path findPath(HexagonMap map, int[] start, int[] end, int endRadius) {
		NodeList openNodes = new NodeList();
		NodeList closedNodes = new NodeList();
		openNodes.add(new Node(start));
		Node found = null;
		for (;;) {
			if (openNodes.size == 0) {
				return null;
			}
			Node node = openNodes.removeIndex(0);
			int distance = map.distance(node.position, end);
			if (distance <= endRadius) {
				found = node;
				break;
			}
			closedNodes.add(node);
			int[][] positions = map.adjacent(node.position);
			for (int[] position : positions) {
				if (!map.isAllowMoved(position)) {
					continue;
				}
				if (closedNodes.find(position) != null) {
					continue;
				}
				Node openNode = openNodes.find(position);
				if (openNode == null) {
					Node newNode = new Node(position);
					newNode.g = node.g + map.getLimitType(position);
					newNode.h = (int) (map.distance(position, end) * map.baseScore(position, end));
					newNode.f = newNode.g + newNode.h;
					newNode.parent = node;
					openNodes.insert(newNode);
				} else {
					int g = node.g + map.getLimitType(position);
					if (openNode.g > g) {
						openNode.g = g;
						openNode.f = openNode.g + openNode.h;
						openNode.parent = node;
						openNodes.sort(openNode);
					}
				}
			}
		}
		if (found == null) {
			return null;
		}
		Path path = new Path();
		path.cost = found.g;
		SortedList<int[]> positions = new SortedList<int[]>();
		while (found != null) {
			positions.addFirst(found.position.clone());
			found = found.parent;
		}
		path.positions = positions;
		return path;
	}

	public HexagonMap() {
		this(null);
	}

	public HexagonMap(String texturePackPath) {
		if (!StringUtils.isEmpty(texturePackPath)) {
			texturePack = new LTexturePack(texturePackPath);
		} else {
			texturePack = new LTexturePack();
		}
		this._scaleX = this._scaleY = 1f;
		this._rotation = 0f;
		this.visible = true;
		this.active = true;
		this.dirty = true;
		this.lastOffsetX = -1;
		this.lastOffsetY = -1;
		this.rectViewTemp = LSystem.viewSize.getRect().cpy();
	}

	public boolean isAllowMoved(int[] position) {
		return contains(position) && getLimitType(position) != -1;
	}

	public int getLimitType(int[] position) {
		int id = getTile(position).idx;
		if (getLimit() != null) {
			for (int i = 0; i < getLimit().length; i++) {
				if (getLimit()[i] == id) {
					return -1;
				}
			}
		}
		return id;
	}

	public float baseScore(int[] position, int[] end) {
		if (heuristic != null) {
			return heuristic.getScore(position[0], position[1], end[0], end[1]);
		}
		return 10f;
	}

	public HexagonMap fillImageIdToTiles(int id, int imgId) {
		int[][] maps = new int[rows][cols];
		for (int j = 0; j < cols; j++) {
			for (int i = 0; i < rows; i++) {
				tiles[i][j] = new TileImpl(id, i, j);
				tiles[i][j].imgId = imgId;
				maps[i][j] = id;
			}
		}
		setFieldMap(maps);
		return this;
	}

	public HexagonMap fillTiles(int id) {
		int[][] maps = new int[rows][cols];
		for (int j = 0; j < cols; j++) {
			for (int i = 0; i < rows; i++) {
				tiles[i][j] = new TileImpl(id, i, j);
				maps[i][j] = id;
			}
		}
		setFieldMap(maps);
		return this;
	}

	public HexagonMap putImageIdTile(int id, int x, int y) {
		getTile(x, y).imgId = id;
		return this;
	}

	public int getImageIdTile(int x, int y) {
		return getTile(x, y).imgId;
	}

	public HexagonMap createMap(int startWidth, int midHeight, int endHeight, int cols, int rows) {
		return createMap(0, 0, startWidth, midHeight, endHeight, cols, rows);
	}

	public HexagonMap createMap(int x, int y, int startWidth, int midHeight, int endHeight, int cols, int rows) {
		configure(new Hexagon(x, y, startWidth, midHeight, endHeight));
		configure(cols, rows);
		return this;
	}

	public HexagonMap configure(Hexagon origin) {
		this.origin = origin;
		return this;
	}

	public HexagonMap configure(int cols, int rows) {
		this.cols = cols;
		this.rows = rows;
		this.tiles = new TileImpl[cols][rows];
		this.hexagons = new Hexagon[cols][rows];
		this.setFieldMap(new int[cols][rows]);
		return this;
	}

	public Hexagon getOrigin() {
		return origin;
	}

	public RectBox getRect() {
		if (rectTemp == null) {
			rectTemp = new RectBox(origin.getX(), origin.getY(), (origin.getX()
					+ (rows > 1 ? cols * origin.getWidth() + origin.getStartWidth() : cols * origin.getWidth())),
					(origin.getY() + rows * origin.getBaseHeight() + origin.getEndHeight()));
		} else {
			rectTemp.setBounds(origin.getX(), origin.getY(), (origin.getX()
					+ (rows > 1 ? cols * origin.getWidth() + origin.getStartWidth() : cols * origin.getWidth())),
					(origin.getY() + rows * origin.getBaseHeight() + origin.getEndHeight()));
		}
		return rectTemp;
	}

	public boolean contains(Vector2f pos) {
		return contains(pos.toInt());
	}

	public boolean contains(int[] position) {
		int m0 = position[0] + (position[1] >> 1);
		return m0 >= 0 && m0 < cols && position[1] >= 0 && position[1] < rows;
	}

	public int[] getLimit() {
		if (field2dMap != null) {
			return field2dMap.getLimit();
		}
		return new int[] { -1 };
	}

	public void setLimit(int[] limitTypes) {
		if (field2dMap != null) {
			field2dMap.setLimit(limitTypes);
		}
	}

	public TileImpl getTile(float x, float y) {
		return getTile(Vector2f.at(x, y));
	}

	public TileImpl getTile(Vector2f pos) {
		return getTile(pos.toInt());
	}

	public TileImpl getTile(int[] position) {
		int x = position[0] + (position[1] >> 1);
		int y = position[1];
		if (y > -1 && y < cols && x > -1 && x < rows) {
			return tiles[x][y];
		}
		return null;
	}

	public HexagonMap setTile(float x, float y, int id) {
		return setTile(Vector2f.at(x, y), new TileImpl(id));
	}

	public HexagonMap setTile(float x, float y, TileImpl tile) {
		return setTile(Vector2f.at(x, y), tile);
	}

	public HexagonMap setTile(Vector2f pos, TileImpl tile) {
		return setTile(pos.toInt(), tile);
	}

	public HexagonMap setTile(int[] position, TileImpl tile) {
		tiles[position[0] + (position[1] >> 1)][position[1]] = tile;
		return this;
	}

	public Hexagon coordinate(Vector2f pos) {
		return coordinate(pos.toInt());
	}

	public Hexagon coordinate(int[] position) {
		int m0 = position[0] + (position[1] >> 1);
		Hexagon hexagon = hexagons[m0][position[1]];
		if (hexagon == null) {
			hexagon = new Hexagon(
					position[0] * origin.getWidth() + position[1] * origin.getStartWidth() + origin.getX(),
					position[1] * origin.getBaseHeight() + origin.getY(), origin.getStartWidth(), origin.getMidHeight(),
					origin.getEndHeight());
			hexagons[m0][position[1]] = hexagon;
		}
		return hexagon;
	}

	public Vector2f decoordinate(int x, int y) {
		int m0, n;
		int xBlock = (x - origin.getX()) / (origin.getStartWidth() + origin.getStartWidth());
		int xOdd = (x - origin.getX()) % (origin.getStartWidth() + origin.getStartWidth());
		int yBlock = (y - origin.getY()) / (origin.getEndHeight() + origin.getMidHeight());
		int yOdd = (y - origin.getY()) % (origin.getEndHeight() + origin.getMidHeight());
		int yOdd0 = MathUtils.round(origin.getEndHeight() / origin.getStartWidth() * xOdd);
		if ((yBlock & 1) == 0) {
			if (yOdd < origin.getEndHeight() - yOdd0) {
				m0 = xBlock - 1;
				n = yBlock - 1;
			} else if (yOdd < yOdd0 - origin.getEndHeight()) {
				m0 = xBlock;
				n = yBlock - 1;
			} else {
				m0 = xBlock;
				n = yBlock;
			}
		} else {
			if (xOdd < origin.getStartWidth()) {
				if (yOdd < yOdd0) {
					m0 = xBlock;
					n = yBlock - 1;
				} else {
					m0 = xBlock - 1;
					n = yBlock;
				}
			} else {
				if (yOdd < origin.getEndHeight() + origin.getEndHeight() - yOdd0) {
					m0 = xBlock;
					n = yBlock - 1;
				} else {
					m0 = xBlock;
					n = yBlock;
				}
			}
		}
		if (m0 >= 0 && m0 < cols && n >= 0 && n < rows) {
			return Vector2f.at(m0 - (n >> 1), n);
		}
		return null;
	}

	public int distance(Vector2f start, Vector2f end) {
		return distance(start.toInt(), end.toInt());
	}

	public int distance(int[] start, int[] end) {
		Integer c = end[0] - start[0];
		Integer r = end[1] - start[1];
		if (c.compareTo(0) == r.compareTo(0)) {
			c = c < 0 ? -c : c;
			r = r < 0 ? -r : r;
			return c + r;
		} else {
			c = c < 0 ? -c : c;
			r = r < 0 ? -r : r;
			return r > c ? r : c;
		}
	}

	public int orientate(Vector2f start, Vector2f end) {
		return orientate(start.toInt(), end.toInt());
	}

	public int orientate(int[] start, int[] end) {
		Integer c = end[0] - start[0];
		Integer r = end[1] - start[1];
		c = c.compareTo(0);
		r = r.compareTo(0);
		return orientationsByHexagon[r + 1][c + 1];
	}

	public AStarFindHeuristic getHeuristic() {
		return heuristic;
	}

	public void setHeuristic(AStarFindHeuristic aheuristic) {
		this.heuristic = aheuristic;
	}

	public int[][] adjacent(int[] position) {
		positions[0][0] = position[0] - 1;
		positions[0][1] = position[1];
		positions[1][0] = position[0];
		positions[1][1] = position[1] - 1;
		positions[2][0] = position[0] + 1;
		positions[2][1] = position[1] - 1;
		positions[3][0] = position[0] + 1;
		positions[3][1] = position[1];
		positions[4][0] = position[0];
		positions[4][1] = position[1] + 1;
		positions[5][0] = position[0] - 1;
		positions[5][1] = position[1] + 1;
		return positions;
	}

	public int[] adjacent(int[] position, int orientation) {
		int[] offset = hexagonByOrientation[orientation + 3];
		this.position[0] = position[0] + offset[0];
		this.position[1] = position[1] + offset[1];
		return this.position;
	}

	public SortedList<int[]> lineRegion(Vector2f start, Vector2f end) {
		return lineRegion(start.toInt(), end.toInt());
	}

	public SortedList<int[]> lineRegion(int[] start, int[] end) {
		int dx = end[0] - start[0];
		int dy = end[1] - start[1];
		if (dx == 0 || dy == 0 || dx == -dy) {
			SortedList<int[]> positions = new SortedList<int[]>();
			int ax = dx < 0 ? -dx : dx;
			int ay = dy < 0 ? -dy : dy;
			int len = ax < ay ? ay : ax;
			int x = start[0];
			int y = start[1];
			int x1 = dx / len;
			int y1 = dy / len;
			for (int i = 0; i <= len; i++) {
				positions.add(new int[] { x, y });
				x += x1;
				y += y1;
			}
			return positions;
		}
		return null;
	}

	public TArray<Vector2f> circleRegion(Vector2f center, int radius) {
		return circleRegion(center.toInt(), radius);
	}

	public TArray<Vector2f> circleRegion(int[] center, int radius) {
		TArray<Vector2f> positions = new TArray<Vector2f>();
		int i, j, k;
		for (j = -radius; j <= radius; j++) {
			if (j < 0) {
				i = -radius - j;
				k = radius;
			} else {
				i = -radius;
				k = radius - j;
			}
			for (; i <= k;) {
				positions.add(Vector2f.at(center[0] + i, center[1] + j));
				i++;
			}
		}
		return positions;
	}

	public RectBox getViewRect() {
		if (viewRect == null) {
			return getRect();
		}
		return viewRect;
	}

	public void setViewRect(RectBox viewRect) {
		this.viewRect = viewRect;
	}

	protected LTexture getTexture(int imgId) {
		return texturePack.getTexture(imgId);
	}

	private LTexture getTempHexagon(Hexagon hex) {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, hex.getWidth());
		hashCode = LSystem.unite(hashCode, hex.getHeight());
		hashCode = LSystem.unite(hashCode, hex.getStartWidth());
		hashCode = LSystem.unite(hashCode, hex.getMidHeight());
		hashCode = LSystem.unite(hashCode, hex.getEndHeight());
		LTexture texture = (LTexture) textureCaches.get(hashCode);
		if (texture == null || texture.isClosed()) {
			texture = createPolyTexture(hex.getPolygon(0, 0), hex.getWidth(), hex.getHeight());
			textureCaches.put(hashCode, texture);
		}
		return texture;
	}

	private LTexture createPolyTexture(Polygon polygon, int w, int h) {
		Pixmap pix = new Pixmap(w, h, true);
		pix.setColor(LColor.white);
		pix.fillPolygon(polygon);
		pix.setColor(LColor.black);
		pix.drawPolygon(polygon);
		return pix.texture();
	}

	private void drawText(GLEx g, Hexagon hexagon, int x, int y, int offX, int offY, LColor color) {
		IFont font = null;
		if (displayFont == null) {
			font = g.getFont();
		} else {
			font = displayFont;
		}
		int[] center = hexagon.getCenter();
		String text = "[" + x + "," + y + "]";
		g.setFont(font);
		g.drawText(text, (center[0] - getViewRect().x - font.stringWidth(text) / 2) + offX,
				(center[1] - getViewRect().y + font.getHeight() / 2) + offY, color);
	}

	public Vector2f getPositionFlag() {
		return positionFlag;
	}

	public void setPositionFlag(Vector2f posFlag) {
		this.positionFlag = posFlag;
	}

	public void setPositionFlag(float x, float y) {
		this.setPositionFlag(Vector2f.at(x, y));
	}

	public TileImpl clicked(float x, float y) {
		x += offsetXPixel(getViewRect().x);
		y += offsetYPixel(getViewRect().y);
		focuses = null;
		Vector2f pos = decoordinate((int) x, (int) y);
		if (pos == null) {
			return null;
		}
		position = pos.toInt();
		if (position != null) {
			TileImpl tile = getTile(position);
			if (allowDisplayClicked) {
				setTile(position, new TileImpl((tile.idx < getLimit().length - 1) ? tile.idx + 1 : 0));
			}
			return tile;
		}
		return null;
	}

	public SortedList<int[]> findPath(int startX, int startY, int endX, int endY) {
		endX += offsetXPixel(getViewRect().x);
		endY += offsetYPixel(getViewRect().y);
		focuses = null;
		Vector2f pos = decoordinate(endX, endY);
		if (pos == null) {
			return null;
		}
		int[] position = pos.toInt();
		if (position != null) {
			Path path = findPath(this, Vector2f.at(startX, startY).toInt(), position);
			if (path != null) {
				focuses = path.positions;
				return new SortedList<>(focuses);
			}
		}
		return null;
	}

	public SortedList<int[]> findPath(float endX, float endY) {
		if (positionFlag == null) {
			return null;
		}
		return findPath(positionFlag.x(), positionFlag.y(), (int) endX, (int) endY);
	}

	public SortedList<int[]> findPath(int endX, int endY) {
		if (positionFlag == null) {
			return null;
		}
		return findPath(positionFlag.x(), positionFlag.y(), endX, endY);
	}

	public SortedList<int[]> findMovePath(int xStart, int yStart, int xEnd, int yEnd) {
		xEnd += offsetXPixel(getViewRect().x);
		yEnd += offsetYPixel(getViewRect().y);
		focuses = null;
		Vector2f position = decoordinate(xEnd, yEnd);
		if (position != null && allowDisplayFindPath) {
			focuses = lineRegion(positionFlag.toInt(), position.toInt());
			return new SortedList<int[]>(focuses);
		}
		return lineRegion(positionFlag.toInt(), position.toInt());
	}

	public boolean isAllowDisplayFindPath() {
		return allowDisplayFindPath;
	}

	public void setAllowDisplayFindPath(boolean displayFindPath) {
		this.allowDisplayFindPath = displayFindPath;
	}

	public boolean isAllowDisplayFlag() {
		return allowDisplayClicked;
	}

	public void setAllowDisplayFlag(boolean displayClicked) {
		this.allowDisplayClicked = displayClicked;
	}

	public HexagonMap setImagePackAuto(String fileName, int tileWidth, int tileHeight) {
		if (texturePack != null) {
			texturePack.close();
			texturePack = null;
		}
		texturePack = new LTexturePack(fileName, LTexturePackClip.getTextureSplit(fileName, tileWidth, tileHeight));
		texturePack.packed(format);
		return this;
	}

	public HexagonMap setImagePack(String fileName, TArray<LTexturePackClip> clips) {
		if (texturePack != null) {
			texturePack.close();
			texturePack = null;
		}
		texturePack = new LTexturePack(fileName, clips);
		texturePack.packed(format);
		return this;
	}

	public HexagonMap setImagePack(String file) {
		if (texturePack != null) {
			texturePack.close();
			texturePack = null;
		}
		texturePack = new LTexturePack(file);
		texturePack.packed(format);
		return this;
	}

	public HexagonMap pack() {
		completed();
		return this;
	}

	public HexagonMap completed() {
		if (texturePack != null) {
			if (!texturePack.isPacked()) {
				texturePack.packed(format);
			}
			int[] list = texturePack.getIdList();
			active = true;
			dirty = true;
			for (int i = 0, size = list.length; i < size; i++) {
				int id = list[i];
				putTile(id, id);
			}
		}
		return this;
	}

	public Format getFormat() {
		return format;
	}

	public void setFormat(Format format) {
		this.format = format;
	}

	public boolean isAllowDisplayPosition() {
		return allowDisplayPosition;
	}

	public void setAllowDisplayPosition(boolean displayPosition) {
		this.allowDisplayPosition = displayPosition;
	}

	@Override
	public void setVisible(boolean v) {
		this.visible = v;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public RectBox getRectBox() {
		return getRect();
	}

	@Override
	public RectBox getCollisionBox() {
		return getRect();
	}

	@Override
	public LTexture getBitmap() {
		return _background;
	}

	@Override
	public void update(long elapsedTime) {
		if (playAnimation && animations.size > 0) {
			for (Animation a : animations) {
				a.update(elapsedTime);
			}
		}
		if (_mapSprites != null) {
			_mapSprites.update(elapsedTime);
		}
		if (listener != null) {
			listener.update(elapsedTime);
		}
	}

	@Override
	public float getWidth() {
		return getRect().getWidth();
	}

	@Override
	public float getHeight() {
		return getRect().getHeight();
	}

	private void setFieldMap(int[][] maps) {
		if (origin != null) {
			if (field2dMap == null) {
				field2dMap = new Field2D(maps, origin.getWidth(), origin.getHeight());
			} else {
				field2dMap.set(maps, origin.getWidth(), origin.getHeight());
			}
		} else if (field2dMap == null) {
			field2dMap = new Field2D(maps);
		} else {
			field2dMap.setMap(maps);
		}
	}

	public boolean isAllowDisplayPosText() {
		return allowDisplayPosText;
	}

	public void setAllowDisplayPosText(boolean displayPosText) {
		this.allowDisplayPosText = displayPosText;
	}

	public ActionBind getFollow() {
		return follow;
	}

	public HexagonMap setFollow(ActionBind follow) {
		this.follow = follow;
		return this;
	}

	public HexagonMap followAction(ActionBind follow) {
		return setFollow(follow);
	}

	public Vector2f toRollPosition(Vector2f pos) {
		pos.x = pos.x % ((float) (getViewRect().width));
		pos.y = pos.y % ((float) (getViewRect().height));
		if (pos.x < 0f) {
			pos.x += getViewRect().width;
		}
		if (pos.x < 0f) {
			pos.y += getViewRect().height;
		}
		return pos;
	}

	public DrawListener<HexagonMap> getListener() {
		return listener;
	}

	public int getRow() {
		return rows;
	}

	public int getCol() {
		return cols;
	}

	public HexagonMap setListener(DrawListener<HexagonMap> liste) {
		this.listener = liste;
		return this;
	}

	public boolean isDirty() {
		return dirty;
	}

	public HexagonMap setDirty(boolean dirty) {
		this.dirty = dirty;
		return this;
	}

	public MoveControl followControl(ActionBind bind) {
		followAction(bind);
		return new MoveControl(bind, this.field2dMap);
	}

	public boolean isRoll() {
		return roll;
	}

	public HexagonMap setRoll(boolean roll) {
		this.roll = roll;
		return this;
	}

	public LTexture getBackground() {
		return this._background;
	}

	public HexagonMap setBackground(LTexture bg) {
		this._background = bg;
		return this;
	}

	@Override
	public ActionTween selfAction() {
		return PlayerUtils.set(this);
	}

	@Override
	public boolean isActionCompleted() {
		return PlayerUtils.isActionCompleted(this);
	}

	public Sprites getMapSprites() {
		return _mapSprites;
	}

	public HexagonMap setMapSprites(Sprites s) {
		_mapSprites = s;
		return this;
	}

	@Override
	public void setSprites(Sprites ss) {
		if (this._sprites == ss) {
			return;
		}
		this._sprites = ss;
	}

	@Override
	public Sprites getSprites() {
		return this._sprites;
	}

	@Override
	public Screen getScreen() {
		if (this._sprites == null) {
			return LSystem.getProcess().getScreen();
		}
		return this._sprites.getScreen() == null ? LSystem.getProcess().getScreen() : this._sprites.getScreen();
	}

	public float getScreenX() {
		float x = 0;
		ISprite parent = _super;
		if (parent != null) {
			x += parent.getX();
			for (; (parent = parent.getParent()) != null;) {
				x += parent.getX();
			}
		}
		return x + getX();
	}

	public float getScreenY() {
		float y = 0;
		ISprite parent = _super;
		if (parent != null) {
			y += parent.getY();
			for (; (parent = parent.getParent()) != null;) {
				y += parent.getY();
			}
		}
		return y + getY();
	}

	@Override
	public float getContainerX() {
		if (_super != null) {
			return getScreenX() - getX();
		}
		return this._sprites == null ? super.getContainerX() : this._sprites.getX();
	}

	@Override
	public float getContainerY() {
		if (_super != null) {
			return getScreenY() - getY();
		}
		return this._sprites == null ? super.getContainerY() : this._sprites.getY();
	}

	@Override
	public float getContainerWidth() {
		return this._sprites == null ? super.getContainerWidth() : this._sprites.getWidth();
	}

	@Override
	public float getContainerHeight() {
		return this._sprites == null ? super.getContainerHeight() : this._sprites.getHeight();
	}

	@Override
	public void createUI(GLEx g) {
		createUI(g, 0, 0);
	}

	@Override
	public void createUI(GLEx g, float offsetX, float offsetY) {
		if (!visible) {
			return;
		}
		boolean update = (_rotation != 0) || !(_scaleX == 1f && _scaleY == 1f);
		int blend = g.getBlendMode();
		int tmp = g.color();
		try {
			g.setBlendMode(_blend);
			g.setAlpha(_alpha);
			if (this.roll) {
				this.offset = toRollPosition(this.offset);
			}
			float newX = this._location.x + offsetX + offset.getX();
			float newY = this._location.y + offsetY + offset.getY();
			if (update) {
				g.saveTx();
				Affine2f tx = g.tx();
				if (_rotation != 0) {
					final float rotationCenterX = newX + getWidth() / 2f;
					final float rotationCenterY = newY + getHeight() / 2f;
					tx.translate(rotationCenterX, rotationCenterY);
					tx.preRotate(_rotation);
					tx.translate(-rotationCenterX, -rotationCenterY);
				}
				if ((_scaleX != 1) || (_scaleY != 1)) {
					final float scaleCenterX = newX + getWidth() / 2f;
					final float scaleCenterY = newY + getHeight() / 2f;
					tx.translate(scaleCenterX, scaleCenterY);
					tx.preScale(_scaleX, _scaleY);
					tx.translate(-scaleCenterX, -scaleCenterY);
				}
			}
			followActionObject();
			int moveX = (int) newX;
			int moveY = (int) newY;
			draw(g, moveX, moveY);
			if (_mapSprites != null) {
				_mapSprites.paintPos(g, moveX, moveY);
			}
		} catch (Exception ex) {
			LSystem.error("Array2D TileMap error !", ex);
		} finally {
			if (update) {
				g.restoreTx();
			}
			g.setBlendMode(blend);
			g.setColor(tmp);
		}

	}

	public float offsetXPixel(float x) {
		return MathUtils.iceil((x - offset.x - _location.x) / _scaleX);
	}

	public float offsetYPixel(float y) {
		return MathUtils.iceil((y - offset.y - _location.y) / _scaleY);
	}

	public boolean inMap(int x, int y) {
		return ((((x >= 0) && (x < getRect().width)) && (y >= 0)) && (y < getRect().height));
	}

	protected float limitOffsetX(float newOffsetX) {
		float offsetX = getContainerWidth() / 2 - newOffsetX;
		offsetX = MathUtils.min(offsetX, 0);
		offsetX = MathUtils.max(offsetX, getContainerWidth() - getWidth());
		return offsetX;
	}

	protected float limitOffsetY(float newOffsetY) {
		float offsetY = getContainerHeight() / 2 - newOffsetY;
		offsetY = MathUtils.min(offsetY, 0);
		offsetY = MathUtils.max(offsetY, getContainerHeight() - getHeight());
		return offsetY;
	}

	public HexagonMap setOffset(float x, float y) {
		this.offset.set(x, y);
		return this;
	}

	public HexagonMap followActionObject() {
		if (follow != null) {
			float offsetX = limitOffsetX(follow.getX());
			float offsetY = limitOffsetY(follow.getY());
			setOffset(offsetX, offsetY);
			field2dMap.setOffset(offset);
		}
		return this;
	}

	@Override
	public LColor getColor() {
		return new LColor(_baseColor);
	}

	@Override
	public void setColor(LColor c) {
		if (c != null && !c.equals(_baseColor)) {
			this._baseColor = c;
			this.dirty = true;
		}
	}

	public int getPixelsAtFieldType(Vector2f pos) {
		return field2dMap.getPixelsAtFieldType(pos.x, pos.y);
	}

	public int getPixelsAtFieldType(float x, float y) {
		int itsX = pixelsToTilesWidth(x);
		int itsY = pixelsToTilesHeight(y);
		return field2dMap.getPixelsAtFieldType(itsX, itsY);
	}

	@Override
	public Field2D getField2D() {
		return field2dMap;
	}

	@Override
	public float getScaleX() {
		return _scaleX;
	}

	@Override
	public float getScaleY() {
		return _scaleY;
	}

	@Override
	public void setScale(float sx, float sy) {
		this._scaleX = sx;
		this._scaleY = sy;
	}

	@Override
	public boolean isBounded() {
		return false;
	}

	@Override
	public boolean isContainer() {
		return true;
	}

	@Override
	public boolean inContains(float x, float y, float w, float h) {
		return field2dMap.getRect().contains(x, y, w, h);
	}

	public void scrollDown(float distance) {
		this.offset.y = limitOffsetY(MathUtils.min((this.offset.y + distance),
				(MathUtils.max(0, this.field2dMap.getViewHeight() - getContainerHeight()))));
	}

	public void scrollLeft(float distance) {
		this.offset.x = limitOffsetX(MathUtils.max(this.offset.x - distance, 0));
	}

	public void scrollLeftUp(float distance) {
		this.scrollUp(distance);
		this.scrollLeft(distance);
	}

	public void scrollRight(float distance) {
		this.offset.x = limitOffsetX(MathUtils.min((this.offset.x + distance),
				(MathUtils.max(0, this.field2dMap.getViewWidth() - getContainerWidth()))));
	}

	public void scrollUp(float distance) {
		this.offset.y = limitOffsetY(MathUtils.max(this.offset.y - distance, 0));
	}

	public void scrollRightDown(float distance) {
		this.scrollDown(distance);
		this.scrollRight(distance);
	}

	public void scrollClear() {
		this.offset.set(0, 0);
	}

	public boolean isHit(int px, int py) {
		return field2dMap.isHit(px, py);
	}

	public boolean isHit(Vector2f v) {
		return isHit(v.x(), v.y());
	}

	public boolean isPixelHit(int px, int py) {
		return isPixelHit(px, py, 0, 0);
	}

	public boolean isPixelHit(int px, int py, int movePx, int movePy) {
		return isHit(field2dMap.pixelsToTilesWidth(field2dMap.offsetXPixel(px)) + movePx,
				field2dMap.pixelsToTilesHeight(field2dMap.offsetYPixel(py)) + movePy);
	}

	public boolean isPixelTUp(int px, int py) {
		return isPixelHit(px, py, 0, -1);
	}

	public boolean isPixelTRight(int px, int py) {
		return isPixelHit(px, py, 1, 0);
	}

	public boolean isPixelTLeft(int px, int py) {
		return isPixelHit(px, py, -1, 0);
	}

	public boolean isPixelTDown(int px, int py) {
		return isPixelHit(px, py, 0, 1);
	}

	public Vector2f getTileCollision(LObject<?> o, float newX, float newY) {
		newX = MathUtils.ceil(newX);
		newY = MathUtils.ceil(newY);

		float fromX = MathUtils.min(o.getX(), newX);
		float fromY = MathUtils.min(o.getY(), newY);
		float toX = MathUtils.max(o.getX(), newX);
		float toY = MathUtils.max(o.getY(), newY);

		int fromTileX = field2dMap.pixelsToTilesWidth(fromX);
		int fromTileY = field2dMap.pixelsToTilesHeight(fromY);
		int toTileX = field2dMap.pixelsToTilesWidth(toX + o.getWidth() - 1f);
		int toTileY = field2dMap.pixelsToTilesHeight(toY + o.getHeight() - 1f);

		for (int x = fromTileX; x <= toTileX; x++) {
			for (int y = fromTileY; y <= toTileY; y++) {
				if ((x < 0) || (x >= field2dMap.getWidth())) {
					return new Vector2f(x, y);
				}
				if ((y < 0) || (y >= field2dMap.getHeight())) {
					return new Vector2f(x, y);
				}
				if (!this.isHit(x, y)) {
					return new Vector2f(x, y);
				}
			}
		}

		return null;
	}

	public int getTileIDFromPixels(Vector2f v) {
		return getTileIDFromPixels(v.x, v.y);
	}

	public int getTileID(int x, int y) {
		if (x >= 0 && x < field2dMap.getWidth() && y >= 0 && y < field2dMap.getHeight()) {
			return field2dMap.getType(y, x);
		} else {
			return -1;
		}
	}

	public int getTileIDFromPixels(float sx, float sy) {
		float x = (sx + offset.getX());
		float y = (sy + offset.getY());
		Vector2f tileCoordinates = pixelsToTiles(x, y);
		return getTileID(MathUtils.round(tileCoordinates.getX()), MathUtils.round(tileCoordinates.getY()));
	}

	public Vector2f pixelsToTiles(float x, float y) {
		float xprime = x / field2dMap.getTileWidth() - 1;
		float yprime = y / field2dMap.getTileHeight() - 1;
		return new Vector2f(xprime, yprime);
	}

	public Field2D getField() {
		return field2dMap;
	}

	public int tilesToPixelsX(float x) {
		return field2dMap.tilesToWidthPixels(x);
	}

	public int tilesToPixelsY(float y) {
		return field2dMap.tilesToHeightPixels(y);
	}

	public int pixelsToTilesWidth(float x) {
		return field2dMap.pixelsToTilesWidth(x);
	}

	public int pixelsToTilesHeight(float y) {
		return field2dMap.pixelsToTilesHeight(y);
	}

	public int[][] getMap() {
		return field2dMap.getMap();
	}

	public boolean isActive() {
		return active;
	}

	public HexagonMap removeTile(int id) {
		for (TileImpl tile : tileBinds) {
			if (tile.idx == id) {
				if (tile.isAnimation) {
					animations.remove(tile.animation);
				}
				tileBinds.remove(tile);
			}
		}
		if (animations.size == 0) {
			playAnimation = false;
		}
		this.dirty = true;
		return this;
	}

	public int putAnimationTile(int id, Animation animation, Attribute attribute) {
		if (active) {
			TileImpl tile = new TileImpl(id);
			tile.imgId = -1;
			tile.attribute = attribute;
			if (animation != null && animation.getTotalFrames() > 0) {
				tile.isAnimation = true;
				tile.animation = animation;
				playAnimation = true;
			}
			animations.add(animation);
			tileBinds.add(tile);
			dirty = true;
			return tile.imgId;
		} else {
			throw LSystem.runThrow("Map is no longer active, you can not add new tiles !");
		}
	}

	public int putAnimationTile(int id, String res, int w, int h, int timer) {
		return putAnimationTile(id, Animation.getDefaultAnimation(res, w, h, timer), null);
	}

	public int putAnimationTile(int id, Animation animation) {
		return putAnimationTile(id, animation, null);
	}

	public int putTileAutoHexagon(int id, Image img) {
		return putTile(id, imageToHexagon(img), null);
	}

	public int putTileAutoHexagon(int id, String path) {
		return putTile(id, imageToHexagon(path), null);
	}

	public int putTile(int id, Image img, Attribute attribute) {
		if (active) {
			TileImpl tile = new TileImpl(id);
			tile.imgId = texturePack.putImage(img);
			tile.attribute = attribute;
			tileBinds.add(tile);
			dirty = true;
			return tile.imgId;
		} else {
			throw LSystem.runThrow("Map is no longer active, you can not add new tiles !");
		}
	}

	public int putTile(int id, Image img) {
		return putTile(id, img, null);
	}

	public int putTile(int id, LTexture img, Attribute attribute) {
		if (active) {
			TileImpl tile = new TileImpl(id);
			tile.imgId = texturePack.putImage(img);
			tile.attribute = attribute;
			tileBinds.add(tile);
			dirty = true;
			return tile.imgId;
		} else {
			throw LSystem.runThrow("Map is no longer active, you can not add new tiles !");
		}
	}

	public int putTile(int id, LTexture img) {
		return putTile(id, img, null);
	}

	public int putTile(int id, String res, Attribute attribute) {
		if (active) {
			TileImpl tile = new TileImpl(id);
			tile.imgId = texturePack.putImage(res);
			tile.attribute = attribute;
			tileBinds.add(tile);
			dirty = true;
			return tile.imgId;
		} else {
			throw LSystem.runThrow("Map is no longer active, you can not add new tiles !");
		}
	}

	public int putTile(int id, String res) {
		return putTile(id, res, null);
	}

	public HexagonMap putTile(int id, int imgId, Attribute attribute) {
		if (active) {
			TileImpl tile = new TileImpl(id);
			tile.imgId = imgId;
			tile.attribute = attribute;
			tileBinds.add(tile);
			dirty = true;
		} else {
			LSystem.runThrow("Map is no longer active, you can not add new tiles !");
		}
		return this;
	}

	public HexagonMap putTile(int id, int imgId) {
		return putTile(id, imgId, null);
	}

	public TileImpl getTile(int id) {
		for (TileImpl tile : tileBinds) {
			if (tile.idx == id) {
				return tile;
			}
		}
		return null;
	}

	protected HexagonMap loadMap(int[][] maps, int startWidth, int midHeight, int endHeight, boolean added) {
		configure(new Hexagon(0, 0, startWidth, midHeight, endHeight));
		configure(maps[0].length, maps.length);
		for (int j = 0; j < rows; j++) {
			for (int i = 0; i < cols; i++) {
				tiles[i][j] = new TileImpl(maps[i][j], i, j);
			}
		}
		if (added) {
			setFieldMap(maps);
		}
		return this;
	}

	public HexagonMap loadCharsMap(String resName, int startWidth, int midHeight, int endHeight) {
		return loadMap(TileMapConfig.loadCharsMap(resName), startWidth, midHeight, endHeight, true);
	}

	public HexagonMap loadMap(String resName, int startWidth, int midHeight, int endHeight) {
		return loadMap(TileMapConfig.loadAthwartArray(resName), startWidth, midHeight, endHeight, true);
	}

	public void draw(GLEx g, int offsetX, int offsetY) {
		if (!visible) {
			return;
		}
		if (_background != null) {
			g.draw(_background, offsetX, offsetY);
		}
		if (!active || texturePack == null) {
			completed();
			return;
		}
		IFont tmpFont = g.getFont();
		try {
			if (allowDisplayFindPath || allowDisplayClicked || allowDisplayPosition) {
				int step = 0;
				for (TileVisit<TileImpl> visit : allTiles(getViewRect())) {
					Hexagon hexagon = coordinate(visit.position);
					if (getViewRect().intersects(hexagon.getFrameRect())) {
						TileImpl tile = visit.tile;
						TileImpl bindImpl = getTile(tile.idx);
						if (bindImpl != null && playAnimation && bindImpl.isAnimation) {
							LTexture texture = bindImpl.animation.getSpriteImage();
							if (texture != null) {
								int newWidth = MathUtils.max(texture.getWidth(), hexagon.getWidth());
								int newHeight = MathUtils.max(texture.getHeight(), hexagon.getHeight());
								g.draw(texture, hexagon.getX() + offsetX, hexagon.getY() + offsetY, newWidth,
										newHeight);
								if (allowDisplayPosText) {
									drawText(g, hexagon, visit.position[0], visit.position[1], offsetX, offsetY,
											fontColor);
								}
							}
						} else if (bindImpl != null) {
							LTexture texture = texturePack.getTexture(bindImpl.imgId);
							if (texture != null) {
								int newWidth = MathUtils.max(texture.getWidth(), hexagon.getWidth());
								int newHeight = MathUtils.max(texture.getHeight(), hexagon.getHeight());
								g.draw(texture, hexagon.getX() + offsetX, hexagon.getY() + offsetY, newWidth,
										newHeight);
							}
							if (allowDisplayPosText) {
								drawText(g, hexagon, visit.position[0], visit.position[1], offsetX, offsetY, fontColor);
							}
						} else if (allowDisplayPosition) {
							if (step > 6) {
								step = 0;
							}
							LColor color = LColor.white;
							switch (tile.idx) {
							case 0:
								color = LColor.red;
								break;
							case 1:
								color = LColor.yellow;
								break;
							case 2:
								color = LColor.blue;
								break;
							case 3:
								color = LColor.cyan;
								break;
							case 4:
								color = LColor.magenta;
								break;
							case 5:
								color = LColor.maroon;
								break;
							default:
								color = LColor.green;
								break;
							}
							step++;
							g.draw(getTempHexagon(hexagon), hexagon.getX() + offsetX, hexagon.getY() + offsetY, color);
						}
						if (allowDisplayPosText) {
							drawText(g, hexagon, visit.position[0], visit.position[1], offsetX, offsetY, fontColor);
						}
					}
					if (allowDisplayFindPath && focuses != null) {
						for (int[] position : focuses) {
							hexagon = coordinate(position);
							if (getViewRect().intersects(hexagon.getFrameRect())) {
								g.draw(getTempHexagon(hexagon), hexagon.getX() + offsetX, hexagon.getY() + offsetY,
										LColor.lightSkyBlue);
								if (allowDisplayPosText) {
									drawText(g, hexagon, position[0], position[1], offsetX, offsetY, fontColor);
								}
							}
						}
					}
					if (allowDisplayClicked && positionFlag != null) {
						int[] position = positionFlag.toInt();
						hexagon = coordinate(position);
						if (getViewRect().intersects(hexagon.getFrameRect())) {
							g.draw(getTempHexagon(hexagon), hexagon.getX() + offsetX, hexagon.getY() + offsetY,
									LColor.pink);
							if (allowDisplayPosText) {
								drawText(g, hexagon, position[0], position[1], offsetX, offsetY, fontColor);
							}
						}
					}
				}
			} else {
				if (texturePack == null || texturePack.closed()) {
					return;
				}
				dirty = dirty || !texturePack.existCache();
				if (!dirty && lastOffsetX == offsetX && lastOffsetY == offsetY && rectViewTemp.equals(getViewRect())) {
					texturePack.postCache();
					if (playAnimation || allowDisplayPosText) {
						for (TileVisit<TileImpl> visit : allTiles(getViewRect())) {
							Hexagon hexagon = coordinate(visit.position);
							if (getViewRect().intersects(hexagon.getFrameRect())) {
								TileImpl tile = visit.tile;
								TileImpl bindImpl = getTile(tile.idx);
								if (bindImpl != null && playAnimation && bindImpl.isAnimation) {
									LTexture texture = bindImpl.animation.getSpriteImage();
									int newWidth = MathUtils.max(texture.getWidth(), hexagon.getWidth());
									int newHeight = MathUtils.max(texture.getHeight(), hexagon.getHeight());
									g.draw(texture, hexagon.getX() + offsetX, hexagon.getY() + offsetY, newWidth,
											newHeight);
								}
								if (allowDisplayPosText) {
									drawText(g, hexagon, visit.position[0], visit.position[1], offsetX, offsetY,
											fontColor);
								}
							}
						}
					}
				} else {
					texturePack.glBegin();
					for (TileVisit<TileImpl> visit : allTiles(getViewRect())) {
						Hexagon hexagon = coordinate(visit.position);
						if (getViewRect().intersects(hexagon.getFrameRect())) {
							TileImpl tile = visit.tile;
							TileImpl bindImpl = getTile(tile.idx);
							if (bindImpl != null && playAnimation && bindImpl.isAnimation) {
								LTexture texture = bindImpl.animation.getSpriteImage();
								if (texture != null) {
									int newWidth = MathUtils.max(texture.getWidth(), hexagon.getWidth());
									int newHeight = MathUtils.max(texture.getHeight(), hexagon.getHeight());
									g.draw(texture, hexagon.getX() + offsetX, hexagon.getY() + offsetY, newWidth,
											newHeight);
								}
							} else if (bindImpl != null) {
								int id = bindImpl.imgId;
								LTexture texture = texturePack.getTexture(id);
								if (texture != null) {
									int newWidth = MathUtils.max(texture.getWidth(), hexagon.getWidth());
									int newHeight = MathUtils.max(texture.getHeight(), hexagon.getHeight());
									texturePack.draw(id, hexagon.getX() + offsetX, hexagon.getY() + offsetY, newWidth,
											newHeight);
								}
							}
							if (allowDisplayPosText) {
								drawText(g, hexagon, visit.position[0], visit.position[1], offsetX, offsetY, fontColor);
							}
						}
					}
					texturePack.glEnd();
					texturePack.saveCache();
					lastOffsetX = offsetX;
					lastOffsetY = offsetY;
					dirty = false;
				}
			}
		} catch (Throwable thr) {
			throw LSystem.runThrow(thr.getMessage(), thr);
		} finally {
			g.setFont(tmpFont);
		}
	}

	public LTexture imageToHexagon(String path) {
		return Hexagon.createImageToHexagon(path, origin, 1, 1);
	}

	public LTexture imageToHexagon(Image img) {
		return Hexagon.createImageToHexagon(img, origin, 1, 1);
	}

	@Override
	public HexagonMap setFont(IFont font) {
		this.displayFont = font;
		return this;
	}

	@Override
	public IFont getFont() {
		return displayFont;
	}

	@Override
	public HexagonMap setFontColor(LColor color) {
		this.fontColor = color;
		return this;
	}

	@Override
	public LColor getFontColor() {
		return fontColor.cpy();
	}

	@Override
	public String toString() {
		return field2dMap == null ? super.toString() : field2dMap.toString();
	}

	public boolean isClosed() {
		return isDisposed();
	}

	@Override
	public void close() {
		roll = false;
		visible = false;
		playAnimation = false;
		animations.clear();
		if (texturePack != null) {
			texturePack.close();
		}
		if (textureCaches != null) {
			for (int i = 0; i < textureCaches.size(); i++) {
				Entry entry = textureCaches.getEntry(i);
				if (entry != null) {
					LTexture texture = (LTexture) entry.getValue();
					if (texture != null) {
						texture.close();
						texture = null;
					}
				}
			}
		}
		if (_mapSprites != null) {
			_mapSprites.close();
		}
		if (_background != null) {
			_background.close();
		}
		setState(State.DISPOSED);
	}

}
