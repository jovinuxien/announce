#!/usr/bin/env python3
"""Turn a MyBatis-Generator (litemall flavour) mapper into a reactive-mybatis-support mapper.

usage: reactify_mapper.py <db-reactive module dir> <Entity>   e.g. LitemallCategory
Reads  dao/<Entity>Mapper.java and dao/<Entity>Mapper.xml, writes the dao/reactive versions,
and deletes the blocking originals.
"""
import re, sys, os
mod, ent = sys.argv[1], sys.argv[2]
base_pkg = "org.linlinjava.announce.db"
jdir = f"{mod}/src/main/java/org/linlinjava/announce/db/dao"
rdir = f"{mod}/src/main/resources/org/linlinjava/announce/db/dao"
old_java, old_xml = f"{jdir}/{ent}Mapper.java", f"{rdir}/{ent}Mapper.xml"
new_java, new_xml = f"{jdir}/reactive/{ent}Mapper.java", f"{rdir}/reactive/{ent}Mapper.xml"

# ---------- XML ----------
x = open(old_xml).read()
table = re.search(r'from\s+(\w+)\s', x).group(1)
x = re.sub(r'\s*<!--\s*WARNING - @mbg\.generated.*?-->', '', x, flags=re.S)
x = x.replace(f'namespace="{base_pkg}.dao.{ent}Mapper"', f'namespace="{base_pkg}.dao.reactive.{ent}Mapper"')
# inserts: selectKey(LAST_INSERT_ID) -> useGeneratedKeys with explicit key column (R2DBC requirement)
x = re.sub(r'(<insert id="\w+" parameterType="[^"]+")>\s*<selectKey[^>]*>\s*SELECT LAST_INSERT_ID\(\)\s*</selectKey>',
           r'\1 useGeneratedKeys="true" keyProperty="id" keyColumn="id">', x, flags=re.S)
assert '<selectKey' not in x, "unexpected selectKey left"
# explicit paging statements (PageHelper is a plugin; plugins are unsupported reactively)
page = f'''
  <select id="selectPageByExample" parameterType="map" resultMap="BaseResultMap">
    select
    <if test="example.distinct">
      distinct
    </if>
    <include refid="Base_Column_List" />
    from {table}
    <if test="example != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
    <if test="example.orderByClause != null">
      order by ${{example.orderByClause}}
    </if>
    limit #{{limit}} offset #{{offset}}
  </select>
  <select id="selectPageByExampleSelective" parameterType="map" resultMap="BaseResultMap">
    select
    <if test="example.distinct">
      distinct
    </if>
    <choose>
      <when test="selective != null and selective.length &gt; 0">
        <foreach collection="selective" item="column" separator=",">
          ${{column.aliasedEscapedColumnName}}
        </foreach>
      </when>
      <otherwise>
        <include refid="Base_Column_List" />
      </otherwise>
    </choose>
    from {table}
    <if test="example != null">
      <include refid="Update_By_Example_Where_Clause" />
    </if>
    <if test="example.orderByClause != null">
      order by ${{example.orderByClause}}
    </if>
    limit #{{limit}} offset #{{offset}}
  </select>
'''
x = x.replace('</mapper>', page + '</mapper>')
header = f'''<!--
  Reactive mapper for {table} (reactive-mybatis-support / R2DBC), derived from the MyBatis Generator output.
  Differences: inserts use useGeneratedKeys/keyColumn; selectPageByExample* replace the PageHelper plugin.
-->
'''
x = x.replace('<mapper namespace=', header + '<mapper namespace=', 1)
os.makedirs(os.path.dirname(new_xml), exist_ok=True); open(new_xml, 'w').write(x)

# ---------- Java ----------
j = open(old_java).read()
j = re.sub(r'\s*/\*\*.*?\*/', '', j, flags=re.S)          # drop generated javadoc
j = j.replace(f'package {base_pkg}.dao;', f'package {base_pkg}.dao.reactive;')
j = j.replace('import java.util.List;\n', '')
j = j.replace(f'import {base_pkg}.domain.{ent}Example;', f'import {base_pkg}.domain.{ent}Example;\nimport reactor.core.publisher.Flux;\nimport reactor.core.publisher.Mono;')
j = re.sub(r'\n(\s+)long (\w+)\(', r'\n\1Mono<Long> \2(', j)
j = re.sub(r'\n(\s+)int (\w+)\(', r'\n\1Mono<Integer> \2(', j)
j = re.sub(r'\n(\s+)List<(\w+)> (\w+)\(', r'\n\1Flux<\2> \3(', j)
j = re.sub(rf'\n(\s+){ent} (\w+)\(', rf'\n\1Mono<{ent}> \2(', j)
page_methods = f'''
    /** {{@link #selectByExample}} with an explicit {{@code LIMIT limit OFFSET offset}}. */
    Flux<{ent}> selectPageByExample(@Param("example") {ent}Example example,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    /** {{@link #selectByExampleSelective}} with an explicit {{@code LIMIT limit OFFSET offset}}. */
    Flux<{ent}> selectPageByExampleSelective(@Param("example") {ent}Example example,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit,
                                              @Param("selective") {ent}.Column... selective);
}}
'''
j = j.rstrip()[:-1].rstrip() + '\n' + page_methods
doc = f'''/**
 * Reactive (R2DBC) mapper for {{@code {table}}}, migrated from the blocking MyBatis generator output.
 * Method names and the {{@link {ent}Example}} criteria API are unchanged; only return types differ
 * (single row: Mono, many rows: Flux, counts / affected rows: Mono of Long / Integer).
 * Paging is explicit via selectPageByExample*; the PageHelper plugin is not available reactively.
 * SQL lives in {{@code org/linlinjava/announce/db/dao/reactive/{ent}Mapper.xml}}.
 */
public interface {ent}Mapper {{'''
j = j.replace(f'public interface {ent}Mapper {{', doc, 1)
j = re.sub(r'\{\n\n+', '{\n', j); j = re.sub(r'\n{3,}', '\n\n', j)
open(new_java, 'w').write(j)
os.remove(old_java); os.remove(old_xml)
print(f"reactified {ent}: table={table}\n  {new_java}\n  {new_xml}")
