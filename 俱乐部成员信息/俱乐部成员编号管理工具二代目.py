import re
import os
from typing import Set, Dict, Tuple

FILE_PATH = "俱乐部成员编号.md"

def ensure_file_exists():
    if not os.path.exists(FILE_PATH):
        with open(FILE_PATH, 'w', encoding='utf-8') as f:
            f.write("# 俱乐部成员编号\n\n")
            f.write("|姓名|编号|学校|\n")
            f.write("|----|----|----|\n")

def load_member_data() -> Tuple[Set[int], Dict[int, Tuple[str, str]]]:
    ensure_file_exists()
    
    numbers = set()
    member_info = {}
    
    try:
        with open(FILE_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        
        lines = content.strip().split('\n')
        
        for line in lines[2:]:
            match = re.match(r'\|(.*?)\|(\d+)\|(.*?)\|', line.strip())
            if match:
                name = match.group(1).strip()
                number = int(match.group(2))
                school = match.group(3).strip()
                
                numbers.add(number)
                member_info[number] = (name, school)
    except Exception as e:
        print(f"读取文件时出错: {e}")
        return set(), {}
    
    return numbers, member_info

def check_number_exists(number: int) -> bool:
    existing_numbers, member_info = load_member_data()
    
    exists = number in existing_numbers
    if exists:
        name, school = member_info[number]
        print(f"编号 {number:04d} 已存在，持有人: {name}，学校: {school}")
    else:
        print(f"编号 {number:04d} 不存在")
    return exists

def assign_min_number() -> int:
    existing_numbers, _ = load_member_data()
    
    for i in range(0, 10000):  # 编号范围是0000-9999
        if i not in existing_numbers:
            return i
    return -1  

def display_all_members():
    _, member_info = load_member_data()
    
    print("\n===== 所有俱乐部成员 =====")
    print("编号\t姓名\t\t学校")
    print("-" * 50)
    
    # 按编号排序
    sorted_numbers = sorted(member_info.keys())
    for number in sorted_numbers:
        name, school = member_info[number]
        print(f"{number:04d}\t{name}\t\t{school}")

def add_new_member(name: str, school: str, number: int):
    """添加新成员到文件"""
    # 读取现有内容
    with open(FILE_PATH, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    
    table_end_index = 0
    for i, line in enumerate(lines):
        if line.startswith('|') and '|' in line[1:]:
            table_end_index = i
    
    new_line = f"|{name}|{number:04d}|{school}|"
    
    # 插入新行到正确位置
    if table_end_index < len(lines) - 1:
        lines.insert(table_end_index + 1, new_line)
    else:
        lines.append(new_line)
    
    # 重新写入文件
    with open(FILE_PATH, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    
    print(f"成功添加新成员: {name} (编号: {number:04d}, 学校: {school})")

def main():
    ensure_file_exists()
    
    while True:
        print("\n===== 俱乐部成员编号管理工具 =====")
        print("1. 检查编号是否存在")
        print("2. 分配最小未占用编号")
        print("3. 显示所有成员信息")
        print("4. 添加新成员")
        print("5. 退出")
        
        choice = input("请选择操作 (1/2/3/4/5): ")
        
        if choice == '1':
            try:
                num = int(input("请输入要检查的编号 (0000-9999): "))
                if num < 0 or num > 9999:
                    print("编号必须在0000到9999之间")
                    continue
                    
                check_number_exists(num)
            except ValueError:
                print("请输入有效的数字")
                
        elif choice == '2':
            min_num = assign_min_number()
            if min_num == -1:
                print("所有编号都已用完")
            else:
                print(f"最小未占用编号是: {min_num:04d}")
                
        elif choice == '3':
            display_all_members()
                
        elif choice == '4':
            print("\n--- 添加新成员 ---")
            name = input("请输入姓名: ")
            school = input("请输入学校: ")
            
            # 分配编号
            number = assign_min_number()
            if number == -1:
                print("所有编号都已用完，无法添加新成员")
                continue
                
            print(f"分配的编号: {number:04d}")
            
            # 确认是否添加
            confirm = input("确认添加此成员吗? (y/n): ")
            if confirm.lower() == 'y':
                add_new_member(name, school, number)
            else:
                print("已取消添加")
                
        elif choice == '5':
            print("感谢使用，再见！")
            break
            
        else:
            print("无效的选择，请重新输入")

if __name__ == "__main__":
    main()