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
package org.test;

import loon.Stage;
import loon.component.LClickButton;
import loon.component.LTextTree;
import loon.component.LTextTree.TreeElement;
import loon.event.Touched;
import loon.utils.TArray;

public class TextTreeTest extends Stage {

	@Override
	public void create() {

		//构建一个文字显示用树
		final LTextTree tree = new LTextTree("请选择职业", 0, 0, 400, 400);

		//点击骑士
		Touched knightTouched = new Touched() {

			@Override
			public void on(float x, float y) {
				tree.clearElement();
				
				tree.setRootName("见习骑士");
				
				TreeElement node1 = tree.addElement("正式骑士");
				TreeElement node1a =  node1.addSub("大骑士");	
				node1a.addSub("圣骑士");
				
				TreeElement node1b = node1.addSub("领主");
				node1b.addSub("大领主").addSub("国王");
				
				TreeElement node2 = tree.addElement("见习龙骑士");
				node2.addSub("龙骑士").addSub("龙领主").addSub("龙帝");
				node2.addSub("魔兽骑士");
				TreeElement node3 = tree.addElement("魔法骑士");
				node3.addSub("秘银骑士");
				node3.addSub("魔剑士");
				tree.pack();
			}
		};

		//点击法师
		Touched magicTouched = new Touched() {

			@Override
			public void on(float x, float y) {
				tree.clearElement();
				
				tree.setRootName("见习法师");
				
				TreeElement node1 = tree.addElement("正式法师");
				TreeElement node1a =  node1.addSub("大法师");	
				node1a.addSub("大魔导师").addSub("魔导皇帝");
				
				TreeElement node1b = node1.addSub("牧师");
				node1b.addSub("贤者").addSub("圣者");
				
				TreeElement node2 = tree.addElement("巫师");
				node2.addSub("黑巫师").addSub("大恶巫师");
				node2.addSub("白巫师").addSub("圣光使者").addSub("泰坦","虚空大君");

				tree.pack();
			}
		};

		//点击剑士
		Touched swordTouched = new Touched() {

			@Override
			public void on(float x, float y) {
				tree.clearElement();
				
				tree.setRootName("见习剑士");
				
				TreeElement node1 = tree.addElement("正式剑士");
				TreeElement node1a =  node1.addSub("大剑士");	
				TArray<TreeElement> list = node1a.addSub("剑君","侠客");
				list.get(0).addSub("剑王").addSub("剑帝","剑魔");
				list.get(1).addSub("巨侠").addSub("武帝");

				TreeElement node2 = tree.addElement("战士");
				node2.addSub("大战士").addSub("神圣战士","恶魔战士").get(0).addSub("天位战士");
				tree.pack();
			}
		};
		
		LClickButton knightBtn = LClickButton.make("骑士", 370, 20, 80, 40);
		knightBtn.up(knightTouched);
		add(knightBtn);

		LClickButton magicBtn = LClickButton.make("魔法师", 370, 70, 80, 40);
		magicBtn.up(magicTouched);
		add(magicBtn);

		LClickButton swordBtn = LClickButton.make("剑士", 370, 120, 80, 40);
		swordBtn.up(swordTouched);
		add(swordBtn);

		add(tree);

		add(MultiScreenTest.getBackButton(this, 1));
	}

}
