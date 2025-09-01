import re
from typing import Set, Dict, Tuple, Optional

def parse_member_data(content: str) -> Tuple[Set[int], Dict[int, Tuple[str, str]]]:
    """解析Markdown内容，提取所有已使用的编号和成员信息"""
    lines = content.strip().split('\n')
    numbers = set()
    member_info = {}  # 字典：编号 -> (姓名, 学校)
    
    # 跳过表头和分隔行
    for line in lines[2:]:
        match = re.match(r'\|(.*?)\|(\d+)\|(.*?)\|', line.strip())
        if match:
            name = match.group(1).strip()
            number = int(match.group(2))
            school = match.group(3).strip()
            
            numbers.add(number)
            member_info[number] = (name, school)
    
    return numbers, member_info

def check_number_exists(number: int, existing_numbers: Set[int], member_info: Dict[int, Tuple[str, str]]) -> bool:
    """检查编号是否存在，如果存在则显示成员信息"""
    exists = number in existing_numbers
    if exists:
        name, school = member_info[number]
        print(f"编号 {number:04d} 已存在，持有人: {name}，学校: {school}")
    else:
        print(f"编号 {number:04d} 不存在")
    return exists

def assign_min_number(existing_numbers: Set[int]) -> int:
    """分配最小未占用的编号"""
    for i in range(0, 10000):  # 编号范围是0000-9999
        if i not in existing_numbers:
            return i
    return -1  # 所有编号都已用完

def display_all_members(member_info: Dict[int, Tuple[str, str]]):
    """显示所有成员信息"""
    print("\n===== 所有俱乐部成员 =====")
    print("编号\t姓名\t\t学校")
    print("-" * 50)
    
    # 按编号排序
    sorted_numbers = sorted(member_info.keys())
    for number in sorted_numbers:
        name, school = member_info[number]
        print(f"{number:04d}\t{name}\t\t{school}")

def main():
    # 文件内容
    content = """# 俱乐部成员编号

|姓名|编号|学校|
|----|----|----|
|慕冬亮|0000|华中科技大学|
|王振辰|0001|华中科技大学|
|陈哲翰|0002|华中科技大学|
|袁令羲|0005|华中科技大学|
|徐必昂|0008|华中科技大学|
|周浔|0009|华中科技大学|
|吴瑞捷|0077|华中科技大学|
|周诗嘉|0111|华中科技大学|
|张志宇|0123|中科院信息工程研究所|
|刘浩阳|0260|香港科技大学|
|姚礼兴|0521|天津工业大学|
|许洋鸣|0606|华中科技大学|
|常续本|0618|香港城市大学|
|葛炳岐|0714|华中科技大学|
|杨许玮|0721|南昌大学|
|万祚全|0730|华中科技大学|
|申珊靛|0739|华中科技大学|
|何沁遥|0816|华中科技大学|
|肖丽莎|0918|华中科技大学|
|李政阳|0930|南昌大学|
|王广锋|1010|华中科技大学|
|尹春媛|1011|河南财经政法大学|
|程子丘|1024|华中科技大学|
|潘俊玮|1037|华中科技大学|
|朱龙豪|1205|华中科技大学|
|王相智|1207|华中科技大学|
|林观韬|1413|华中科技大学|
|胡崟昊|1767|华中科技大学|
|刘浩毅|2025|华中科技大学|
|杜清云|2030|华中科技大学|
|易炜涵|2223|南昌大学|
|丁鹏宇|2233|华中科技大学|
|朱凯峰|2537|华中科技大学|
|周一航|3030|华中科技大学|
|刘为军|3623|华中科技大学|
|邹锦阳|3636|华中科技大学|
|易承志|4520|华中科技大学|
|李朝阳|5003|华中科技大学|
|梅开彦|5283|华中科技大学|
|周旸|5555|华中科技大学|
|邝嘉诺|6666|华中科技大学|
|董庆|7777|华中科技大学|
|辛哲麒|8848|华中科技大学|
|蒋周奇|8888|华中科技大学|
|徐梓航|9527|郑州大学|
|朱俊星|9999|华中科技大学|"""
    
    # 解析现有编号和成员信息
    existing_numbers, member_info = parse_member_data(content)
    
    while True:
        print("\n===== 俱乐部成员编号管理工具 =====")
        print("1. 检查编号是否存在")
        print("2. 分配最小未占用编号")
        print("3. 显示所有成员信息")
        print("4. 退出")
        
        choice = input("请选择操作 (1/2/3/4): ")
        
        if choice == '1':
            try:
                num = int(input("请输入要检查的编号 (0000-9999): "))
                if num < 0 or num > 9999:
                    print("编号必须在0000到9999之间")
                    continue
                    
                check_number_exists(num, existing_numbers, member_info)
            except ValueError:
                print("请输入有效的数字")
                
        elif choice == '2':
            min_num = assign_min_number(existing_numbers)
            if min_num == -1:
                print("所有编号都已用完")
            else:
                print(f"最小未占用编号是: {min_num:04d}")
                
        elif choice == '3':
            display_all_members(member_info)
                
        elif choice == '4':
            print("感谢使用，再见！")
            break
            
        else:
            print("无效的选择，请重新输入")

if __name__ == "__main__":
    main()